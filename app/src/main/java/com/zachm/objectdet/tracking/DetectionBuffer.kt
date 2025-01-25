package com.zachm.objectdet.tracking

import android.graphics.Rect
import android.util.Log
import com.zachm.objectdet.util.BoundingBox

class DetectionBuffer() {
    private val trackedDetections: MutableMap<Int,Detection> = mutableMapOf()
    private val predictedDetections: MutableMap<Int,Detection> = mutableMapOf()
    private val detectionBuffer: MutableMap<Int,Int> = mutableMapOf()
    private var itemCount: Int = 1
    val threshold: Float = 0.65f
    var bboxes: BoundingBox? = null

    fun addDetections(detections: List<Detection>) {

        val boxes = mutableListOf<Rect>()
        val scores = mutableListOf<Float>()
        val items = mutableListOf<String>()
        val remove = mutableSetOf<Int>()

        detections.forEach { current ->
            var matched = false

            trackedDetections.forEach { tracked ->
                if(calculateIOU(current.bbox, tracked.value.bbox) > threshold) {

                    current.id = tracked.value.id
                    current.velocity = calculateVelocity(current.bbox, tracked.value.bbox)

                    matched = true
                }
            }

            if(!matched) {
                predictedDetections.forEach { predicted->
                    if(calculateIOU(current.bbox, predicted.value.bbox) > threshold) {
                        current.velocity = calculateVelocity(current.bbox, trackedDetections[current.id]?.bbox ?: current.bbox)
                        matched = true
                    }
                }
            }

            if(matched) {
                boxes.add(current.bbox)
                scores.add(current.score)
                items.add(current.item + " #" +current.id.toString())

                trackedDetections[current.id] = current
                predictedDetections[current.id] = calculateKalmanFilter(current)
                detectionBuffer[current.id] = 0
            }

            if(!matched && current.id == 0) {
                current.id = itemCount
                itemCount++
                trackedDetections[current.id] = current
                detectionBuffer[current.id] = 0
            }

            if(!matched && current.id != 0 && detectionBuffer.contains(current.id)) {
                detectionBuffer[current.id] = detectionBuffer[current.id]!! + 1

                if(detectionBuffer[current.id]!! > 10) {
                    remove.add(current.id)
                }
            }
        }

        bboxes = BoundingBox(boxes,scores,items)
        if(itemCount >= 100) { itemCount = 1 }
        remove.forEach { trackedDetections.remove(it) }
    }

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

    private fun calculateVelocity(current: Rect, old: Rect): Pair<Int, Int> {
        var x = current.centerX() - old.centerX()
        var y = current.centerY() - old.centerY()
        return Pair(x,y)
    }

    private fun calculateKalmanFilter(detection: Detection) : Detection {
        var box = Rect(
            detection.bbox.left - detection.velocity.first,
            detection.bbox.top - detection.velocity.second,
            detection.bbox.right - detection.velocity.first,
            detection.bbox.bottom - detection.velocity.second
        )
        return Detection(box,detection.score,detection.item,detection.velocity)
    }
}

data class Detection(val bbox: Rect, val score: Float, var item: String, var velocity: Pair<Int, Int> = Pair(0,0), var id: Int = 0)