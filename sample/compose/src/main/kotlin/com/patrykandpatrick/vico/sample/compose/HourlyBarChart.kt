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

package com.patrykandpatrick.vico.sample.compose

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil
import kotlin.math.max

/**
 * Custom ItemPlacer that adjusts label spacing based on zoom level.
 * Shows labels at intervals of 4 hours when zoomed out,
 * and progressively shows more labels (every 2 hours, then every 1 hour) as user zooms in.
 * Minimum interval is 1 hour (no minutes).
 */
private class DynamicHourlyItemPlacer(
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
      visibleXCount <= 6 -> 1   // Zoomed in: show every hour
      visibleXCount <= 12 -> 2  // Medium zoom: show every 2 hours  
      else -> 4                 // Zoomed out: show every 4 hours
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

// Custom value formatter to display hours
private val BottomAxisValueFormatter =
  object : CartesianValueFormatter {
    override fun format(
      context: CartesianMeasuringContext,
      value: Double,
      verticalAxisPosition: Axis.Position.Vertical?,
    ) = "${value.toInt()}h"
  }

@Composable
private fun JetpackComposeHourlyBarChart(
  modelProducer: CartesianChartModelProducer,
  modifier: Modifier = Modifier,
) {
  CartesianChartHost(
    chart =
      rememberCartesianChart(
        rememberColumnCartesianLayer(),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis =
          HorizontalAxis.rememberBottom(
            valueFormatter = BottomAxisValueFormatter,
            itemPlacer = remember { DynamicHourlyItemPlacer() },
          ),
      ),
    modelProducer = modelProducer,
    modifier = modifier.height(300.dp),
    zoomState = rememberVicoZoomState(zoomEnabled = true),
  )
}

// Sample data representing 24 hours
private val x = (0..23).toList()
private val y = listOf(5, 7, 6, 4, 3, 2, 1, 3, 6, 9, 12, 15, 14, 13, 16, 18, 17, 15, 12, 10, 8, 7, 6, 5)

@Composable
fun JetpackComposeHourlyBarChart(modifier: Modifier = Modifier) {
  val modelProducer = remember { CartesianChartModelProducer() }
  LaunchedEffect(Unit) {
    modelProducer.runTransaction {
      columnSeries { series(x, y) }
    }
  }
  JetpackComposeHourlyBarChart(modelProducer, modifier)
}

@Composable
@Preview
private fun Preview() {
  val modelProducer = remember { CartesianChartModelProducer() }
  // Use `runBlocking` only for previews, which don't support asynchronous execution.
  runBlocking {
    modelProducer.runTransaction {
      columnSeries { series(x, y) }
    }
  }
  PreviewBox { JetpackComposeHourlyBarChart(modelProducer) }
}
