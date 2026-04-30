package com.erayoz.uberapp.data.remote

import com.google.gson.annotations.SerializedName

data class DirectionsResponse(
    @SerializedName("routes") val routes: List<Route>,
    @SerializedName("status") val status: String
)

data class Route(
    @SerializedName("overview_polyline") val overviewPolyline: OverviewPolyline,
    @SerializedName("legs") val legs: List<Leg>
)

data class OverviewPolyline(
    @SerializedName("points") val points: String
)

data class Leg(
    @SerializedName("distance") val distance: Distance,
    @SerializedName("duration") val duration: Duration,
    @SerializedName("end_address") val endAddress: String,
    @SerializedName("start_address") val startAddress: String
)

data class Distance(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Int
)

data class Duration(
    @SerializedName("text") val text: String,
    @SerializedName("value") val value: Int
)
