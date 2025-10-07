# Monthly Bar Chart with Dynamic Zoom-Based Labels

This sample demonstrates a bar chart that displays monthly/daily data (31 days of October) with dynamic label spacing based on zoom level.

## Features

- **Dynamic Label Spacing**: The chart automatically adjusts label density based on the zoom level
- **Smart Label Intervals**: 
  - Zoomed out (viewing >14 days): Shows labels every 7 days (6/10, 13/10, 20/10, 27/10)
  - Medium zoom (viewing 7-14 days): Shows labels every 3 days (3/10, 6/10, 9/10, 12/10, etc.)
  - Zoomed in (viewing ≤7 days): Shows labels every day (1/10, 2/10, 3/10, 4/10, etc.)
- **Minimum Label Interval**: Labels never go below 1 day (no hours/minutes support)

## Implementation

The implementation uses a custom `HorizontalAxis.ItemPlacer` called `DynamicDailyItemPlacer` that:

1. Calculates the number of visible x-values in the current viewport
2. Determines appropriate spacing based on the visible range
3. Generates label positions that are multiples of the spacing interval (7 days, 3 days, or 1 day)
4. Ensures smooth scrolling with overflow buffers

## Files

### Compose
- `sample/compose/src/main/kotlin/com/patrykandpatrick/vico/sample/compose/MonthlyBarChart.kt`

### Multiplatform
- `sample/multiplatform/src/commonMain/kotlin/com/patrykandpatrick/vico/sample/multiplatform/MonthlyBarChart.kt`

### Views (Android)
- `sample/views/src/main/kotlin/com/patrykandpatrick/vico/sample/views/MonthlyBarChart.kt`
- `sample/views/src/main/res/layout/monthly_bar_chart.xml`
- `sample/views/src/main/res/values/monthly_bar_chart_styles.xml`

## Usage

The chart is enabled with zoom by default:

```kotlin
CartesianChartHost(
  chart = rememberCartesianChart(
    rememberColumnCartesianLayer(),
    startAxis = VerticalAxis.rememberStart(),
    bottomAxis = HorizontalAxis.rememberBottom(
      valueFormatter = BottomAxisValueFormatter,
      itemPlacer = remember { DynamicDailyItemPlacer() },
    ),
  ),
  modelProducer = modelProducer,
  zoomState = rememberVicoZoomState(zoomEnabled = true),
)
```

## How It Works

The `DynamicDailyItemPlacer` class implements the `HorizontalAxis.ItemPlacer` interface and overrides the `getLabelValues` method to:

1. Calculate `visibleXCount` - the number of data points visible in the current view
2. Determine `spacing` based on `visibleXCount`:
   - spacing = 1 when visibleXCount ≤ 7 (show every day)
   - spacing = 3 when 7 < visibleXCount ≤ 14 (show every 3 days)
   - spacing = 7 when visibleXCount > 14 (show every 7 days)
3. Generate labels that are multiples of the spacing value
4. Add overflow values for smooth panning

The value formatter displays days as "1/10", "2/10", "3/10", etc. (day/month format).

## Sample Data

The chart displays 31 days of October with sample data representing varying values throughout the month:

```kotlin
val x = (1..31).toList()
val y = listOf(
  45, 52, 48, 55, 50, 47, 53, // Week 1
  60, 65, 62, 70, 68, 72, 75, // Week 2
  78, 80, 77, 82, 85, 88, 90, // Week 3
  87, 84, 80, 83, 78, 75, 72, // Week 4
  70, 68, 65, 62              // Week 5
)
```

## Comparison with Hourly Chart

This monthly chart follows the same pattern as the hourly bar chart but is adapted for daily/monthly data:

| Aspect | Hourly Chart | Monthly Chart |
|--------|-------------|---------------|
| Data Range | 24 hours (0-23) | 31 days (1-31) |
| Zoomed Out Spacing | 4 hours | 7 days |
| Medium Zoom Spacing | 2 hours | 3 days |
| Zoomed In Spacing | 1 hour | 1 day |
| Minimum Interval | 1 hour | 1 day |
| Label Format | "0h", "4h", "8h" | "1/10", "7/10", "14/10" |
