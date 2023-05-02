package org.opengl.model;


import org.joml.Vector3f;
import org.joml.Vector4f;

public class Material {

    public static final Material DEFAULT_MATERIAL = new Material(new Vector3f(), 0, 0);
    private Vector3f diffuse;
    public float roughness;
    public float metallic;

    public Vector3f getDiffuse() {
        return diffuse;
    }

    public Material(Vector3f diffuse, float roughness, float metallic) {
        this.diffuse = diffuse;
        this.roughness = roughness;
        this.metallic = metallic;
    }
}
