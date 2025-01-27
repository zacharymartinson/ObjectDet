package com.zachm.objectdet.tracking

import android.graphics.Rect
import android.util.Log
import com.zachm.objectdet.util.BoundingBox
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Object Tracking Relying on Kalman Filtering, IoU, and Dynamic Thresholding written for android.
 * Its similar to a basic SORT alg.
 * By Zachary Martinson * 2025
 */

class DetectionBuffer() {
    private val trackedDetections: MutableMap<Int,Detection> = mutableMapOf()
    private val predictedDetections: MutableMap<Int,Detection> = mutableMapOf()
    private val detectionBuffer: MutableMap<Int,Long> = mutableMapOf()
    private val ids: MutableSet<Int> = mutableSetOf()
    private val scaleFactor: Double = 2.0
    private val biasFactor: Double = 0.2
    val threshold: Float = 0.75f
    var bboxes: BoundingBox? = null

    fun addDetections(detections: List<Detection>) {

        val boxes = mutableListOf<Rect>()
        val scores = mutableListOf<Float>()
        val items = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()

        //Check all NEW detections
        detections.forEach { current ->
            var matched = false
            var predict = false

            //Check our predicted detections from our kalman step (velocity prediction)
            predictedDetections.forEach { predicted->
                if(calculateThreshold(calculateIOU(current.bbox, predicted.value.bbox), predicted.value.confidence) > (threshold - calculateScaledVelocity(predicted.value.velocity, predicted.value.bbox)) && current.item == predicted.value.item) {
                    matched = true
                    predict = true

                    //We update confidence based on predictions only.
                    current.confidenceSet = predicted.value.confidenceSet
                    current.confidenceSet.add(calculateIOU(current.bbox, predicted.value.bbox).toDouble())
                    current.id = predicted.value.id
                }
            }

            trackedDetections.forEach { tracked ->
                if(calculateThreshold(calculateIOU(current.bbox, tracked.value.bbox), tracked.value.confidence) > (threshold - calculateScaledVelocity(tracked.value.velocity, tracked.value.bbox)) && current.item == tracked.value.item) {

                    //Edge case for when it is the first detection from IoU (Happens when too much movement that we track a new detection)
                    if(tracked.value.id == 0) {
                        tracked.value.id = generateID()
                    }

                    current.id = tracked.value.id
                    current.velocity = calculateVelocity(current.bbox, tracked.value.bbox)


                    //If we don't have this in prediction we set the confidence to be slightly lower
                    //BiasFactor is 0.2 because theres less confidence in this case.
                    if(!predict) {
                        current.confidenceSet = tracked.value.confidenceSet
                        current.confidenceSet.add(calculateIOU(current.bbox, tracked.value.bbox).toDouble() - biasFactor)
                    }

                    matched = true
                }
            }

            //When we successfully tracked.
            if(matched) {
                boxes.add(current.bbox)
                scores.add(current.score)
                items.add("${current.item} #${current.id}")

                current.confidenceSet.add(0.0)

                trackedDetections[current.id] = current
                predictedDetections[current.id] = calculateKalmanFilter(current)
                detectionBuffer[current.id] = currentTime
            }

            //First time detection
            if(!matched && current.id == 0) {
                current.id = generateID()
                current.confidenceSet.add(1.0)
                trackedDetections[current.id] = current
                detectionBuffer[current.id] = currentTime
            }

            //Calculate confidence using kotlin HashSet.average() which is O(n).
            current.confidence = current.confidenceSet.average()

        }


        //We use an interator here because of concurrent exceptions
        //Bascially iterating while modifying the map is bad and causes lag and stuttering.
        val iterator = detectionBuffer.entries.iterator()

        while(iterator.hasNext()) {
            val entry = iterator.next()
            if(currentTime - entry.value > 1000L) {
                iterator.remove()
                trackedDetections[entry.key]?.confidenceSet?.clear() //Recycle HEAP Memory from MutableSet
                predictedDetections[entry.key]?.confidenceSet?.clear() //Recycle HEAP Memory from MutableSet
                predictedDetections.remove(entry.key)
                trackedDetections.remove(entry.key)
                ids.remove(entry.key)
            }
        }

        //Update our boxes to be rendered
        bboxes = BoundingBox(boxes,scores,items)
    }

    /**
     * Clears the buffer.
     */
    fun clear() {
        trackedDetections.clear()
        predictedDetections.clear()
        detectionBuffer.clear()
        ids.clear()
    }

    /**
     * Generates a new ID for a detection.
     */
    private fun generateID(): Int {
        //Could use Random.nextInt() here to save time potentially?
        for(id in 1 until 99) {
            if(!ids.contains(id)) {
                ids.add(id)
                return id
            }
        }
        return 0
    }

    /**
     * Calculates threshold based on average iou and confidence.
     */
    private fun calculateThreshold(iou: Float, bias: Double) : Double {
        return (iou + bias) / 2
    }

    /**
     * Uses euclidean distance to get a max displacement (distance) of a box. This is then scaled to the box width + height.
     */
    private fun calculateScaledVelocity(velocity: Pair<Int,Int>, rect: Rect): Double {
        val displacementDistance = sqrt(abs(velocity.first).toDouble().pow(2.0) + abs(velocity.second).toDouble().pow(2.0))
        return (displacementDistance / (rect.width() + rect.height())) * scaleFactor
    }

    /**
     * Calculates the intersection over union of two boxes.
     */
    private fun calculateIOU(current: Rect, old: Rect): Float {
        //To get intersection values in Android
        val intersection = Rect(
            maxOf(current.left, old.left),
            maxOf(current.top, old.top),
            minOf(current.right, old.right),
            minOf(current.bottom, old.bottom)
        )

        //Gets the intersection while keeping the floor to 0
        val intersectionWidth = maxOf(0, intersection.width())
        val intersectionHeight = maxOf(0, intersection.height())
        val intersectionArea = intersectionWidth * intersectionHeight

        //Area is just a surface area from math
        val currentArea = current.width() * current.height()
        val oldArea = old.width() * old.height()

        //Finding surface area for the intersection (if it exists)
        val unionArea = currentArea + oldArea - intersectionArea

        //Handle edge case, then return percentage filled.
        if (unionArea <= 0) { return 0f }
        return intersectionArea.toFloat() / unionArea.toFloat()
    }

    /**
     * Calculates the velocity of a box.
     */
    private fun calculateVelocity(current: Rect, old: Rect): Pair<Int, Int> {
        var x = current.centerX() - old.centerX()
        var y = current.centerY() - old.centerY()
        return Pair(x,y)
    }

    /**
     * Calculates the kalman filter for a detection.
     */
    private fun calculateKalmanFilter(detection: Detection) : Detection {
        var box = Rect(
            detection.bbox.left - detection.velocity.first,
            detection.bbox.top - detection.velocity.second,
            detection.bbox.right - detection.velocity.first,
            detection.bbox.bottom - detection.velocity.second
        )
        return Detection(box,detection.score,detection.item,detection.velocity,detection.id,detection.confidence,detection.confidenceSet)
    }
}

data class Detection(val bbox: Rect, val score: Float, var item: String, var velocity: Pair<Int, Int> = Pair(0,0), var id: Int = 0, var confidence: Double = 1.0, var confidenceSet: MutableSet<Double> = mutableSetOf())