# Hourly Bar Chart with Dynamic Zoom-Based Labels

This sample demonstrates a bar chart that displays hourly data (0-23 hours) with dynamic label spacing based on zoom level.

## Features

- **Dynamic Label Spacing**: The chart automatically adjusts label density based on the zoom level
- **Smart Label Intervals**: 
  - Zoomed out (viewing >12 hours): Shows labels every 4 hours (0h, 4h, 8h, 12h, 16h, 20h, 24h)
  - Medium zoom (viewing 6-12 hours): Shows labels every 2 hours (0h, 2h, 4h, 6h, 8h, etc.)
  - Zoomed in (viewing ≤6 hours): Shows labels every hour (0h, 1h, 2h, 3h, 4h, etc.)
- **Minimum Label Interval**: Labels never go below 1 hour (no minutes support)

## Implementation

The implementation uses a custom `HorizontalAxis.ItemPlacer` called `DynamicHourlyItemPlacer` that:

1. Calculates the number of visible x-values in the current viewport
2. Determines appropriate spacing based on the visible range
3. Generates label positions that are multiples of the spacing interval
4. Ensures smooth scrolling with overflow buffers

## Files

### Compose
- `sample/compose/src/main/kotlin/com/patrykandpatrick/vico/sample/compose/HourlyBarChart.kt`

### Multiplatform
- `sample/multiplatform/src/commonMain/kotlin/com/patrykandpatrick/vico/sample/multiplatform/HourlyBarChart.kt`

### Views (Android)
- `sample/views/src/main/kotlin/com/patrykandpatrick/vico/sample/views/HourlyBarChart.kt`
- `sample/views/src/main/res/layout/hourly_bar_chart.xml`
- `sample/views/src/main/res/values/hourly_bar_chart_styles.xml`

## Usage

The chart is enabled with zoom by default:

```kotlin
CartesianChartHost(
  chart = rememberCartesianChart(
    rememberColumnCartesianLayer(),
    startAxis = VerticalAxis.rememberStart(),
    bottomAxis = HorizontalAxis.rememberBottom(
      valueFormatter = BottomAxisValueFormatter,
      itemPlacer = remember { DynamicHourlyItemPlacer() },
    ),
  ),
  modelProducer = modelProducer,
  zoomState = rememberVicoZoomState(zoomEnabled = true),
)
```

## How It Works

The `DynamicHourlyItemPlacer` class implements the `HorizontalAxis.ItemPlacer` interface and overrides the `getLabelValues` method to:

1. Calculate `visibleXCount` - the number of data points visible in the current view
2. Determine `spacing` based on `visibleXCount`:
   - spacing = 1 when visibleXCount ≤ 6
   - spacing = 2 when 6 < visibleXCount ≤ 12
   - spacing = 4 when visibleXCount > 12
3. Generate labels that are multiples of the spacing value
4. Add overflow values for smooth panning

The value formatter displays hours as "0h", "1h", "2h", etc.

## Sample Data

The chart displays 24 hours of sample data representing activity levels throughout a day:
```kotlin
val x = (0..23).toList()
val y = listOf(5, 7, 6, 4, 3, 2, 1, 3, 6, 9, 12, 15, 14, 13, 16, 18, 17, 15, 12, 10, 8, 7, 6, 5)
```
