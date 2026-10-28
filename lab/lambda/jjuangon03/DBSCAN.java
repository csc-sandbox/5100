package edu.calpoly.lambdalab.jjuangon03;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Instructor-provided DBSCAN implementation for the Gaze Cluster Lambda Lab.
 * Students should use this class as-is and focus on Lambda/API integration.
 */
public final class DBSCAN {
    private DBSCAN() { }

    /** A normalized gaze point. */
    public record GazePoint(double x, double y) { }

    /** A cluster summary returned to the Lambda layer. */
    public record Cluster(int id, double centerX, double centerY, int points) { }

    /**
     * Groups gaze points using DBSCAN.
     *
     * @param points normalized gaze points
     * @param epsilon maximum distance between neighboring points
     * @param minPoints minimum neighborhood size for a core point
     * @return cluster summaries; noise points are omitted
     */
    public static List<Cluster> findClusters(
            List<GazePoint> points, double epsilon, int minPoints) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        if (epsilon <= 0 || minPoints <= 0) {
            throw new IllegalArgumentException("epsilon and minPoints must be greater than zero");
        }

        Set<Integer> visited = new HashSet<>();
        int[] labels = new int[points.size()];
        java.util.Arrays.fill(labels, -1);
        int clusterId = 0;
        for (int i = 0; i < points.size(); i++) {
            if (visited.contains(i)) {
                continue;
            }
            visited.add(i);
            List<Integer> neighbors = neighbors(points, i, epsilon);
            if (neighbors.size() < minPoints) {
                continue;
            }
            expandCluster(points, i, neighbors, clusterId++, epsilon, minPoints, visited, labels);
        }
        return summarize(points, labels, clusterId);
    }

    private static void expandCluster(
            List<GazePoint> points, int pointIndex, List<Integer> initialNeighbors,
            int clusterId, double epsilon, int minPoints, Set<Integer> visited, int[] labels) {
        labels[pointIndex] = clusterId;
        Queue<Integer> queue = new ArrayDeque<>(initialNeighbors);
        Set<Integer> queued = new HashSet<>(initialNeighbors);
        while (!queue.isEmpty()) {
            int q = queue.remove();
            if (!visited.contains(q)) {
                visited.add(q);
                List<Integer> qNeighbors = neighbors(points, q, epsilon);
                if (qNeighbors.size() >= minPoints) {
                    for (int neighbor : qNeighbors) {
                        if (queued.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            if (labels[q] == -1) {
                labels[q] = clusterId;
            }
        }
    }

    private static List<Integer> neighbors(List<GazePoint> points, int pointIndex, double epsilon) {
        List<Integer> result = new ArrayList<>();
        GazePoint point = points.get(pointIndex);
        for (int i = 0; i < points.size(); i++) {
            if (distance(point, points.get(i)) <= epsilon) {
                result.add(i);
            }
        }
        return result;
    }

    private static double distance(GazePoint a, GazePoint b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static List<Cluster> summarize(List<GazePoint> points, int[] labels, int numberOfClusters) {
        List<Cluster> result = new ArrayList<>();
        for (int clusterId = 0; clusterId < numberOfClusters; clusterId++) {
            double sumX = 0;
            double sumY = 0;
            int count = 0;
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] == clusterId) {
                    sumX += points.get(i).x();
                    sumY += points.get(i).y();
                    count++;
                }
            }
            if (count > 0) {
                result.add(new Cluster(clusterId, sumX / count, sumY / count, count));
            }
        }
        return result;
    }
}
