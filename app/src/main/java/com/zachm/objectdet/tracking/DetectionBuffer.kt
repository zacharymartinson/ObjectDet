package com.zachm.objectdet.tracking

import android.graphics.Rect
import android.util.Log
import com.zachm.objectdet.util.BoundingBox

class DetectionBuffer() {
    private val trackedDetections: MutableMap<Int,Detection> = mutableMapOf()
    private val predictedDetections: MutableMap<Int,Detection> = mutableMapOf()
    private val detectionBuffer: MutableMap<Int,Long> = mutableMapOf()
    private val ids: MutableSet<Int> = mutableSetOf()
    val threshold: Float = 0.65f
    var bboxes: BoundingBox? = null

    fun addDetections(detections: List<Detection>) {

        val boxes = mutableListOf<Rect>()
        val scores = mutableListOf<Float>()
        val items = mutableListOf<String>()
        val currentTime = System.currentTimeMillis()

        detections.forEach { current ->
            var matched = false

            predictedDetections.forEach { predicted->
                if(calculateIOU(current.bbox, predicted.value.bbox) > threshold && current.item == predicted.value.item) {
                    matched = true
                }
            }

            trackedDetections.forEach { tracked ->
                if(calculateIOU(current.bbox, tracked.value.bbox) > threshold && current.item == tracked.value.item) {

                    if(tracked.value.id == 0) {
                        tracked.value.id = generateID()
                    }

                    current.id = tracked.value.id
                    current.velocity = calculateVelocity(current.bbox, tracked.value.bbox)

                    matched = true
                }
            }

            if(matched) {
                boxes.add(current.bbox)
                scores.add(current.score)
                items.add(current.item + " #" +current.id.toString())

                trackedDetections[current.id] = current
                predictedDetections[current.id] = calculateKalmanFilter(current)
                detectionBuffer[current.id] = currentTime
            }

            if(!matched && current.id == 0) {
                current.id = generateID()
                trackedDetections[current.id] = current
                detectionBuffer[current.id] = currentTime
            }
        }


        //We use an interator here because of concurrent exceptions
        //Bascially iterating while modifying the map is bad and causes lag and stuttering.
        val iterator = detectionBuffer.entries.iterator()

        while(iterator.hasNext()) {
            val entry = iterator.next()
            if(currentTime - entry.value > 1000L) {
                iterator.remove()
                predictedDetections.remove(entry.key)
                trackedDetections.remove(entry.key)
                ids.remove(entry.key)
            }
        }

        bboxes = BoundingBox(boxes,scores,items)
    }

    fun clear() {
        trackedDetections.clear()
        predictedDetections.clear()
        detectionBuffer.clear()
        ids.clear()
    }

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