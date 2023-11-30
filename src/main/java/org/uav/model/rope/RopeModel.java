package org.uav.model.rope;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.uav.importer.VerticesLoader;
import org.uav.scene.shader.Shader;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public class RopeModel {
    private final int segmentCount;
    private int VAO;
    private final Shader ropeShader;
    private Vector3f pointA;
    private Vector3f pointB;
    private final Vector3f color1;
    private final Vector3f color2;
    private float ropeLength;
    private final float ropeThickness;
    private float a;
    private float xOffset;
    private float yOffset;
    private boolean useLinear;

    public RopeModel(int segmentCount, float ropeThickness, Shader ropeShader, Vector3f color1, Vector3f color2) {
        this.ropeShader = ropeShader;
        this.segmentCount = segmentCount;
        this.ropeLength = 0;
        this.ropeThickness = ropeThickness;
        pointA = new Vector3f();
        pointB = new Vector3f();
        this.color1 = color1;
        this.color2 = color2;
        a = 0;
        xOffset = 0;
        yOffset = 0;
        useLinear = false;
        setupPoints();
    }

    public void setParameters(Vector3f pointA, Vector3f pointB, float ropeLength) {
        this.pointA = pointA;
        this.pointB = pointB;
        this.ropeLength = ropeLength;
        recalculateCatenary();
    }

    public void draw() {
        ropeShader.use();
        ropeShader.setVec3("pointA", pointA);
        ropeShader.setVec3("pointB", pointB);
        ropeShader.setVec3("color1", color1);
        ropeShader.setVec3("color2", color2);
        ropeShader.setFloat("thickness", ropeThickness);
        ropeShader.setFloat("a", a);
        ropeShader.setFloat("xOffset", xOffset);
        ropeShader.setFloat("yOffset", yOffset);
        ropeShader.setBool("useLinear", useLinear);
        glBindVertexArray(VAO);
        glDrawArrays(GL_POINTS, 0, segmentCount);
        glBindVertexArray(0);
    }

    static final float EPSILON = 0.001f;

    private void recalculateCatenary() {
        float s = ropeLength;
        float h = (float)Math.sqrt(Math.pow(pointB.x - pointA.x,2) + Math.pow(pointB.y - pointA.y,2));
        float distance = (float)Math.sqrt(Math.pow(pointB.x - pointA.x,2) + Math.pow(pointB.y - pointA.y,2) + Math.pow(pointB.z - pointA.z,2));
        if(distance > ropeLength) {
            useLinear = true;
            a = -(pointB.z - pointA.z) / h;
            xOffset =  -h;
            yOffset = -(pointB.z - pointA.z);
            return;
        }
        useLinear = false;
        float v = Math.abs(pointB.z - pointA.z);
        float lhs = (float)(1 / Math.sqrt(( Math.sqrt(s*s - v*v) / h) - 1));
        float b = newtonRaphson(
                x -> (float)(1 / Math.sqrt(2 * x * Math.sinh(1/(2*x)) - 1) - lhs),
                x -> (float)((1/(2*x) * Math.cosh(1/(2*x)) - Math.sinh(1/(2*x))) * Math.pow(2*x*Math.sinh(1/(2*x)) - 1,-1.5)),
                0.05f
        );
        a = b * h;
        calculateOffsets(a);
    }

    private void calculateOffsets(float a) {
        var pivotPoint = new Vector3f(pointB).sub(pointA);
        var pivotPoint2D = new Vector2f((float)Math.sqrt(pivotPoint.x*pivotPoint.x + pivotPoint.y*pivotPoint.y), -pivotPoint.z);
        xOffset = newtonRaphson(
                x -> (float)(a * (Math.cosh((pivotPoint2D.x+x)/a) - Math.cosh(x/a)) - pivotPoint2D.y),
                x -> (float)(Math.sinh((pivotPoint2D.x+x)/a) - Math.sinh(x/a)),
                pivotPoint2D.x/2
        );
        yOffset = (float) (-a * Math.cosh((xOffset)/a));
    }

    static float newtonRaphson(Function<Float, Float> func, Function<Float, Float> derivFunc, float x)
    {
        float h = func.apply(x) / derivFunc.apply(x);
        while (Math.abs(h) >= EPSILON)
        {
            h = func.apply(x) / derivFunc.apply(x);
            x = x - h;
        }
        return x;
    }

    private void setupPoints() {
        List<RopeVertex> vertices = IntStream.range(0, segmentCount).mapToObj(i -> new RopeVertex(
                (float) i/(segmentCount-1),
                i==segmentCount-1? -1: (float) (i + 1) / (segmentCount-1)
        )).toList();

        VAO = glGenVertexArrays();
        int VBO = glGenBuffers();
        glBindVertexArray(VAO);
        glBindBuffer(GL_ARRAY_BUFFER, VBO);
        glBufferData(GL_ARRAY_BUFFER, VerticesLoader.loadToFloatBuffer(vertices), GL_STATIC_DRAW);

        glVertexAttribPointer(0, 1, GL_FLOAT, true, RopeVertex.NUMBER_OF_FLOATS * 4, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 1, GL_FLOAT, true, RopeVertex.NUMBER_OF_FLOATS * 4, 4);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }
}
