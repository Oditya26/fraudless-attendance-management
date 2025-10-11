package com.example.herenow.util

import kotlin.math.*

object LocationChecker {
    fun distanceInMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meter
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun isWithinOffset(
        currentLat: Double, currentLon: Double,
        targetLat: Double, targetLon: Double,
        offsetMeters: Double
    ): Boolean {
        val distance = distanceInMeters(currentLat, currentLon, targetLat, targetLon)
        return distance <= offsetMeters
    }
}
