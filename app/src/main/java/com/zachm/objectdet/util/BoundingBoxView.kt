package com.zachm.objectdet.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View

/**
 * A view created for implementation of Bounding Boxes with the goal of Object Detection.
 *
 * Zachary Martinson * 2024
 */
class BoundingBoxView(ctx: Context, attributes: AttributeSet?): View(ctx, attributes) {
    private var boxes: List<Rect> = listOf()
    private var scores: List<Float> = listOf()
    private var items: List<String> = listOf()

    private var frames = 0
    private var fps = 0
    private var elapsed = 0L
    private var previous = 0L

    var trackFPS: Boolean = false

    private val boxPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40F
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }

    private val textBoxPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.FILL
    }

    fun update(boxes: List<Rect>, scores: List<Float>, items:List<String>) {
        this.boxes = boxes
        this.scores = scores
        this.items = items
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if(boxes.isNotEmpty() && scores.isNotEmpty() && items.isNotEmpty()) {
            for(i in boxes.indices) {
                canvas.drawRect(boxes[i], boxPaint)
                canvas.drawRect(Rect(boxes[i].left-3,boxes[i].top-50,boxes[i].left+350,boxes[i].top),textBoxPaint) //Text background rect.
                canvas.drawText("${items[i]} ${scores[i]*100}%", boxes[i].left.toFloat(), boxes[i].top.toFloat()-10,textPaint)
            }
        }
        else if(boxes.isNotEmpty()) {
            for(i in boxes.indices) {
                canvas.drawRect(boxes[i], boxPaint)
            }
        }

        if(trackFPS) {
            val time = System.nanoTime()
            frames++

            if(previous == 0L) {
                previous = time
            }
            else {
                elapsed = time - previous
                if(elapsed >= 1000000000L) {
                    fps = frames
                    frames = 0
                    previous = time
                }
            }
            canvas.drawText("Box FPS:${fps}",10F,50F,textPaint)
        }
    }
}