package com.cesarcosmico.framesort.service;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceServiceTest {

    private static final double EPSILON = 1e-9;
    private static final Vector FROM = new Vector(10, 64, -3);
    private static final Vector AXIS = new Vector(1, 0, 0);
    private static final Vector U = new Vector(0, 1, 0);
    private static final Vector V = new Vector(0, 0, 1);

    private static Vector point(double distance, double phase) {
        return TraceService.helixPoint(FROM, AXIS, U, V, distance, phase);
    }

    @Test
    void pointsSitOnTheRadiusAtTheirDistanceAlongTheAxis() {
        for (double distance = 0; distance < 5; distance += 0.37) {
            Vector offset = point(distance, 0).subtract(FROM);
            assertEquals(distance, offset.getX(), EPSILON);
            assertEquals(TraceService.HELIX_RADIUS, Math.hypot(offset.getY(), offset.getZ()), EPSILON);
        }
    }

    @Test
    void theTwoStrandsAreOpposite() {
        Vector first = point(0.8, 0);
        Vector second = point(0.8, Math.PI);
        Vector centre = first.clone().add(second).multiply(0.5);
        assertEquals(0, centre.distance(FROM.clone().add(new Vector(0.8, 0, 0))), EPSILON);
    }

    @Test
    void oneTurnEveryTurnLength() {
        Vector start = point(0, 0).subtract(FROM);
        Vector turned = point(TraceService.TURN_LENGTH, 0).subtract(FROM)
                .subtract(new Vector(TraceService.TURN_LENGTH, 0, 0));
        assertEquals(0, start.distance(turned), EPSILON);
    }
}
