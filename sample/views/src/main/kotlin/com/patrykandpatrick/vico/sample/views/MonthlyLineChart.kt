/*
 * Copyright 2025 by Patryk Goworowski and Patrick Michalik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.patrykandpatrick.vico.sample.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidViewBinding
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.sample.views.databinding.MonthlyLineChartBinding
import kotlin.math.max

/**
 * Custom ItemPlacer that adjusts label spacing based on zoom level.
 * Shows labels at intervals of 7 days when zoomed out,
 * and progressively shows more labels (every 3 days, then every 1 day) as user zooms in.
 * Minimum interval is 1 day (no hours/minutes).
 */
private class DynamicDailyItemPlacer(
  private val month: Int = 10, // October for display
  private val shiftExtremeLines: Boolean = true,
  private val addExtremeLabelPadding: Boolean = true,
) : HorizontalAxis.ItemPlacer {

  override fun getShiftExtremeLines(context: CartesianDrawingContext) = shiftExtremeLines

  override fun getFirstLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) =
    if (addExtremeLabelPadding) context.ranges.minX else null

  override fun getLastLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) =
    if (addExtremeLabelPadding) context.ranges.maxX else null

  override fun getLabelValues(
    context: CartesianDrawingContext,
    visibleXRange: ClosedFloatingPointRange<Double>,
    fullXRange: ClosedFloatingPointRange<Double>,
    maxLabelWidth: Float,
  ): List<Double> = with(context) {
    // Calculate available space per label
    val visibleWidth = (visibleXRange.endInclusive - visibleXRange.start) / ranges.xStep * layerDimensions.xSpacing
    val visibleXCount = (visibleXRange.endInclusive - visibleXRange.start) / ranges.xStep + 1
    
    // Determine spacing based on visible range to prevent label overlap
    // The spacing ensures labels don't overlap while showing more detail when zoomed in
    val spacing = when {
      visibleXCount <= 7 -> 1   // Zoomed in: show every day
      visibleXCount <= 14 -> 3  // Medium zoom: show every 3 days  
      else -> 7                 // Zoomed out: show every 7 days
    }

    // Build list of label values
    val values = mutableListOf<Double>()
    val minX = ranges.minX
    val xStep = ranges.xStep
    val minXOffset = minX % xStep
    
    // Calculate the first label value that's a multiple of spacing
    val remainder = ((visibleXRange.start - minX) / xStep) % spacing
    val firstValue = visibleXRange.start + (spacing - remainder) % spacing * xStep
    
    var multiplier = -2 // Add overflow for smooth scrolling
    while (true) {
      var value = firstValue + multiplier * spacing * xStep
      // Normalize to xStep to avoid floating point errors
      value = xStep * ((value - minXOffset) / xStep).let { 
        if (it < 0) kotlin.math.ceil(it) else kotlin.math.floor(it) 
      } + minXOffset
      
      if (value < ranges.minX || value == fullXRange.start) {
        multiplier++
        continue
      }
      if (value > ranges.maxX || value == fullXRange.endInclusive) break
      
      values += value
      
      if (value > visibleXRange.endInclusive + spacing * xStep) break
      multiplier++
    }
    
    values
  }

  override fun getLineValues(
    context: CartesianDrawingContext,
    visibleXRange: ClosedFloatingPointRange<Double>,
    fullXRange: ClosedFloatingPointRange<Double>,
    maxLabelWidth: Float,
  ) = getLabelValues(context, visibleXRange, fullXRange, maxLabelWidth)

  override fun getHeightMeasurementLabelValues(
    context: CartesianMeasuringContext,
    layerDimensions: CartesianLayerDimensions,
    fullXRange: ClosedFloatingPointRange<Double>,
    maxLabelWidth: Float,
  ): List<Double> = with(context) {
    buildList {
      add(ranges.minX)
      if (ranges.xLength < ranges.xStep) return@buildList
      add(ranges.minX + ranges.xStep * kotlin.math.floor(ranges.xLength / ranges.xStep))
    }
  }

  override fun getWidthMeasurementLabelValues(
    context: CartesianMeasuringContext,
    layerDimensions: CartesianLayerDimensions,
    fullXRange: ClosedFloatingPointRange<Double>,
  ): List<Double> = if (addExtremeLabelPadding) {
    with(context) {
      buildList {
        add(ranges.minX)
        if (ranges.xLength < ranges.xStep) return@buildList
        add(ranges.minX + ranges.xStep * kotlin.math.floor(ranges.xLength / ranges.xStep))
      }
    }
  } else {
    emptyList()
  }

  override fun getStartLayerMargin(
    context: CartesianMeasuringContext,
    layerDimensions: CartesianLayerDimensions,
    tickThickness: Float,
    maxLabelWidth: Float,
  ): Float {
    val tickSpace = if (shiftExtremeLines) tickThickness else tickThickness / 2f
    return (tickSpace - layerDimensions.unscalableStartPadding).coerceAtLeast(0f)
  }

  override fun getEndLayerMargin(
    context: CartesianMeasuringContext,
    layerDimensions: CartesianLayerDimensions,
    tickThickness: Float,
    maxLabelWidth: Float,
  ): Float {
    val tickSpace = if (shiftExtremeLines) tickThickness else tickThickness / 2f
    return (tickSpace - layerDimensions.unscalableEndPadding).coerceAtLeast(0f)
  }
}

// Custom value formatter to display days as "6/10", "13/10", etc.
private val bottomAxisValueFormatter =
  object : CartesianValueFormatter {
    override fun format(
      context: CartesianMeasuringContext,
      value: Double,
      verticalAxisPosition: Axis.Position.Vertical?,
    ) = "${value.toInt()}/10"
  }

@Composable
fun ViewMonthlyLineChart(modifier: Modifier) {
  val modelProducer = remember { CartesianChartModelProducer() }
  LaunchedEffect(Unit) {
    modelProducer.runTransaction {
      // Sample data representing 31 days of October
      val x = (1..31).toList()
      val y = listOf(
        45, 52, 48, 55, 50, 47, 53, // Week 1
        60, 65, 62, 70, 68, 72, 75, // Week 2
        78, 80, 77, 82, 85, 88, 90, // Week 3
        87, 84, 80, 83, 78, 75, 72, // Week 4
        70, 68, 65, 62              // Week 5
      )
      lineSeries { series(x, y) }
    }
  }
  AndroidViewBinding(
    { inflater, parent, attachToParent ->
      MonthlyLineChartBinding.inflate(inflater, parent, attachToParent).apply {
        with(chartView) {
          modelProducer = modelProducer
          chart?.bottomAxis?.let { axis ->
            if (axis is HorizontalAxis<*>) {
              (chart as? com.patrykandpatrick.vico.core.cartesian.CartesianChart)?.let { chart ->
                val updatedAxis = axis.copy(
                  valueFormatter = bottomAxisValueFormatter,
                  itemPlacer = DynamicDailyItemPlacer()
                )
                this.chart = chart.copy(bottomAxis = updatedAxis as HorizontalAxis<Axis.Position.Horizontal.Bottom>)
              }
            }
          }
        }
      }
    },
    modifier,
  )
}
