package com.zachm.objectdet.tracking

import android.graphics.Rect
import com.zachm.objectdet.util.BoundingBox

class DetectionBuffer() {
    private val trackedDetections: MutableMap<String,Detection> = mutableMapOf()
    private val buffer: MutableMap<Int, List<Detection>> = mutableMapOf()
    private val predictedDetections: MutableMap<String,Detection> = mutableMapOf()
    private var frameCount: Int = 0
    private var itemCount: Int = 1
    val threshold: Float = 0.65f
    var bboxes: BoundingBox? = null

    fun addDetections(detections: List<Detection>) {

        val boxes = mutableListOf<Rect>()
        val scores = mutableListOf<Float>()
        val items = mutableListOf<String>()
        val indexes: MutableSet<Int> = mutableSetOf()
        val remove: MutableSet<String> = mutableSetOf()

        buffer[frameCount] = detections

        predictedDetections.clear()

        if (!buffer[frameCount - 1].isNullOrEmpty()) {
            val oldDetections = buffer[frameCount - 1]!!

            detections.forEach { currentDetection ->
                oldDetections.forEach { oldDetection ->
                    if(calculateIOU(currentDetection.bbox,oldDetection.bbox) > threshold) {
                        currentDetection.velocity = calculateVelocity(currentDetection.velocity,oldDetection.velocity)
                        currentDetection.item = "${currentDetection.item} #$itemCount"
                        itemCount++
                        trackedDetections[currentDetection.item] = currentDetection
                    }
                }
            }
        }
        else {
            detections.forEach {
                it.item = "${it.item} #$itemCount"
                itemCount++
                trackedDetections[it.item] = it
            }
        }

        trackedDetections.forEach { tracked ->
            detections.forEach { current ->
                if(calculateIOU(tracked.value.bbox,current.bbox) > threshold) {
                    current.velocity = calculateVelocity(current.velocity,tracked.value.velocity)
                    current.item = tracked.value.item

                    val predict = calculateKalmanFilter(current)
                    predictedDetections[predict.item] = predict
                }
            }
        }

        predictedDetections.forEach { predict ->
            trackedDetections.forEach { tracked ->
                if(calculateIOU(predict.value.bbox,tracked.value.bbox) > threshold && predict.value.item == tracked.value.item) {
                    detections.forEachIndexed { index, current ->
                        if(calculateIOU(current.bbox,predict.value.bbox) > threshold && index !in indexes) {
                            boxes.add(current.bbox)
                            scores.add(current.score)
                            items.add(tracked.value.item)
                            indexes.add(index)
                        }
                    }
                }
            }
        }


        bboxes = BoundingBox(boxes,scores,items)
        buffer.remove(frameCount-3)
        remove.forEach { trackedDetections.remove(it) }

        if(itemCount >= 100) { itemCount = 1 }

        frameCount++
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

    private fun calculateVelocity(current: Pair<Int,Int>, old: Pair<Int,Int>): Pair<Int, Int> {
        var x = current.first - old.first
        var y = current.second - old.second
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

data class Detection(val bbox: Rect, val score: Float, var item: String, var velocity: Pair<Int, Int> = Pair(0,0))