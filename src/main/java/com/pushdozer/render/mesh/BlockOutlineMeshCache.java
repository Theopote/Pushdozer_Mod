package com.pushdozer.render.mesh;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Greedy exterior mesh generator for block volume previews.
 * Adapted from Director's BlockOutlineMeshCache.
 */
public final class BlockOutlineMeshCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");

    private Mesh cachedMesh;
    private int cachedHash;

    public record Quad(BlockPos p1, BlockPos p2, BlockPos p3, BlockPos p4, Direction dir) {}
    public record Line(double x1, double y1, double z1, double x2, double y2, double z2) {}
    public record Mesh(List<Quad> quads, List<Line> lines) {}

    public Mesh getOrCreateMesh(Collection<BlockPos> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new Mesh(Collections.emptyList(), Collections.emptyList());
        }

        int hash = computeStableHash(blocks);
        if (cachedMesh != null && cachedHash == hash) {
            return cachedMesh;
        }

        Set<Long> solid = toSolidSet(blocks);
        Mesh mesh = generateMeshInternal(solid);
        cachedMesh = mesh;
        cachedHash = hash;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generated preview mesh: {} quads, {} lines", mesh.quads().size(), mesh.lines().size());
        }

        return mesh;
    }

    private int computeStableHash(Collection<BlockPos> blocks) {
        int xor = 0;
        long sum = 0L;
        for (BlockPos pos : blocks) {
            long packed = pos.asLong();
            xor ^= Long.hashCode(packed);
            sum += packed;
        }
        int hash = 1;
        hash = 31 * hash + blocks.size();
        hash = 31 * hash + xor;
        hash = 31 * hash + Long.hashCode(sum);
        return hash;
    }

    private Set<Long> toSolidSet(Collection<BlockPos> blocks) {
        Set<Long> solid = new HashSet<>(blocks.size() * 2);
        for (BlockPos pos : blocks) {
            solid.add(pos.asLong());
        }
        return solid;
    }

    private Mesh generateMeshInternal(Set<Long> solid) {
        if (solid.isEmpty()) {
            return new Mesh(List.of(), List.of());
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long packed : solid) {
            BlockPos pos = BlockPos.fromLong(packed);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        List<Quad> quads = greedyQuadMerge(solid, minX, minY, minZ, maxX, maxY, maxZ);
        List<Line> lines = greedyLineMerge(quads);
        return new Mesh(quads, lines);
    }

    private List<Quad> greedyQuadMerge(Set<Long> solid,
                                       int minX, int minY, int minZ,
                                       int maxX, int maxY, int maxZ) {
        List<Quad> result = new ArrayList<>();
        int[] coord = new int[3];

        for (Direction dir : Direction.values()) {
            int axis = dir.getAxis().ordinal();
            boolean positive = dir.getDirection() == Direction.AxisDirection.POSITIVE;
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;

            int axisMin = axis == 0 ? minX : axis == 1 ? minY : minZ;
            int axisMax = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            int uMin = u == 0 ? minX : u == 1 ? minY : minZ;
            int uMax = u == 0 ? maxX : u == 1 ? maxY : maxZ;
            int vMin = v == 0 ? minX : v == 1 ? minY : minZ;
            int vMax = v == 0 ? maxX : v == 1 ? maxY : maxZ;
            int uSize = uMax - uMin + 1;
            int vSize = vMax - vMin + 1;

            for (int face = axisMin; face <= axisMax + 1; face++) {
                boolean[][] mask = new boolean[uSize][vSize];

                for (int uu = uMin; uu <= uMax; uu++) {
                    for (int vv = vMin; vv <= vMax; vv++) {
                        int solidCoord = positive ? face - 1 : face;
                        int emptyCoord = positive ? face : face - 1;

                        boolean solidExists = false;
                        boolean emptyExists = false;

                        if (solidCoord >= axisMin && solidCoord <= axisMax) {
                            coord[axis] = solidCoord;
                            coord[u] = uu;
                            coord[v] = vv;
                            solidExists = solid.contains(BlockPos.asLong(coord[0], coord[1], coord[2]));
                        }

                        if (emptyCoord >= axisMin && emptyCoord <= axisMax) {
                            coord[axis] = emptyCoord;
                            coord[u] = uu;
                            coord[v] = vv;
                            emptyExists = solid.contains(BlockPos.asLong(coord[0], coord[1], coord[2]));
                        }

                        if (solidExists != emptyExists) {
                            mask[uu - uMin][vv - vMin] = true;
                        }
                    }
                }

                for (int iu = 0; iu < uSize; iu++) {
                    for (int iv = 0; iv < vSize; ) {
                        if (!mask[iu][iv]) {
                            iv++;
                            continue;
                        }

                        int width = 1;
                        while (iv + width < vSize && mask[iu][iv + width]) {
                            width++;
                        }

                        int height = 1;
                        outer:
                        while (iu + height < uSize) {
                            for (int k = 0; k < width; k++) {
                                if (!mask[iu + height][iv + k]) {
                                    break outer;
                                }
                            }
                            height++;
                        }

                        int u0 = uMin + iu;
                        int u1 = uMin + iu + height;
                        int v0 = vMin + iv;
                        int v1 = vMin + iv + width;

                        int[] p1c = new int[3];
                        int[] p2c = new int[3];
                        int[] p3c = new int[3];
                        int[] p4c = new int[3];

                        p1c[axis] = face;
                        p2c[axis] = face;
                        p3c[axis] = face;
                        p4c[axis] = face;
                        p1c[u] = u0;
                        p1c[v] = v0;
                        p2c[u] = u1;
                        p2c[v] = v0;
                        p3c[u] = u1;
                        p3c[v] = v1;
                        p4c[u] = u0;
                        p4c[v] = v1;

                        result.add(new Quad(
                            new BlockPos(p1c[0], p1c[1], p1c[2]),
                            new BlockPos(p2c[0], p2c[1], p2c[2]),
                            new BlockPos(p3c[0], p3c[1], p3c[2]),
                            new BlockPos(p4c[0], p4c[1], p4c[2]),
                            dir
                        ));

                        for (int du = 0; du < height; du++) {
                            for (int dv = 0; dv < width; dv++) {
                                mask[iu + du][iv + dv] = false;
                            }
                        }
                        iv += width;
                    }
                }
            }
        }

        return result;
    }

    private List<Line> greedyLineMerge(List<Quad> quads) {
        if (quads.isEmpty()) {
            return List.of();
        }

        Map<EdgeKey, List<IntInterval>> groups = new HashMap<>();
        for (Quad quad : quads) {
            BlockPos[] vertices = {quad.p1(), quad.p2(), quad.p3(), quad.p4()};
            addQuadEdge(vertices[0], vertices[1], groups);
            addQuadEdge(vertices[1], vertices[2], groups);
            addQuadEdge(vertices[2], vertices[3], groups);
            addQuadEdge(vertices[3], vertices[0], groups);
        }

        List<Line> lines = new ArrayList<>();
        for (Map.Entry<EdgeKey, List<IntInterval>> entry : groups.entrySet()) {
            List<IntInterval> intervals = entry.getValue();
            if (intervals.isEmpty()) {
                continue;
            }

            intervals.sort(Comparator.comparingInt(interval -> interval.start));
            int currentStart = intervals.getFirst().start;
            int currentEnd = intervals.getFirst().end;
            for (int i = 1; i < intervals.size(); i++) {
                IntInterval interval = intervals.get(i);
                if (interval.start <= currentEnd) {
                    currentEnd = Math.max(currentEnd, interval.end);
                } else {
                    addMergedLine(lines, entry.getKey(), currentStart, currentEnd);
                    currentStart = interval.start;
                    currentEnd = interval.end;
                }
            }
            addMergedLine(lines, entry.getKey(), currentStart, currentEnd);
        }

        return lines;
    }

    private void addQuadEdge(BlockPos a, BlockPos b, Map<EdgeKey, List<IntInterval>> map) {
        int dx = Integer.compare(b.getX(), a.getX());
        int dy = Integer.compare(b.getY(), a.getY());
        int dz = Integer.compare(b.getZ(), a.getZ());

        Direction.Axis axis;
        int start;
        int end;
        int c1;
        int c2;

        if (dx != 0 && dy == 0 && dz == 0) {
            axis = Direction.Axis.X;
            start = Math.min(a.getX(), b.getX());
            end = Math.max(a.getX(), b.getX());
            c1 = a.getY();
            c2 = a.getZ();
        } else if (dy != 0 && dx == 0 && dz == 0) {
            axis = Direction.Axis.Y;
            start = Math.min(a.getY(), b.getY());
            end = Math.max(a.getY(), b.getY());
            c1 = a.getX();
            c2 = a.getZ();
        } else if (dz != 0 && dx == 0 && dy == 0) {
            axis = Direction.Axis.Z;
            start = Math.min(a.getZ(), b.getZ());
            end = Math.max(a.getZ(), b.getZ());
            c1 = a.getX();
            c2 = a.getY();
        } else {
            return;
        }

        if (start == end) {
            return;
        }

        EdgeKey key = new EdgeKey(axis, c1, c2);
        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new IntInterval(start, end));
    }

    private void addMergedLine(List<Line> out, EdgeKey key, int start, int end) {
        if (start == end) {
            return;
        }

        double x1;
        double y1;
        double z1;
        double x2;
        double y2;
        double z2;

        switch (key.axis) {
            case X -> {
                x1 = start;
                x2 = end;
                y1 = y2 = key.c1;
                z1 = z2 = key.c2;
            }
            case Y -> {
                y1 = start;
                y2 = end;
                x1 = x2 = key.c1;
                z1 = z2 = key.c2;
            }
            case Z -> {
                z1 = start;
                z2 = end;
                x1 = x2 = key.c1;
                y1 = y2 = key.c2;
            }
            default -> {
                return;
            }
        }

        out.add(new Line(x1, y1, z1, x2, y2, z2));
    }

    private record EdgeKey(Direction.Axis axis, int c1, int c2) {}

    private record IntInterval(int start, int end) {}
}
