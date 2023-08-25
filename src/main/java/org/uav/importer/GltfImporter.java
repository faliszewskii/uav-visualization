package org.uav.importer;

import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.GltfModelReader;
import de.javagl.jgltf.model.v2.GltfModelV2;
import org.javatuples.Pair;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.uav.animation.Animation;
import org.uav.animation.AnimationPlayer;
import org.uav.model.*;
import org.uav.scene.LoadingScreen;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load;

public class GltfImporter {

    public static final String UNSUPPORTED_ANIMATION_MODEL = "Unsupported animation model";
    private List<TextureModel> textureModels;
    private final Map<String, Texture> loadedTextures;
    private String textureDirectory;
    private final LoadingScreen loadingScreen;
    private Map<String, AnimationModel.Sampler> translationAnimationSamplers;
    private Map<String, AnimationModel.Sampler> rotationAnimationSamplers;
    private Map<String, AnimationModel.Sampler> scaleAnimationSamplers;

    public GltfImporter(LoadingScreen loadingScreen){
        loadedTextures = new HashMap<>();
        this.loadingScreen = loadingScreen;
    }

    public Model loadModel(String resourceFile, String textureDir) throws URISyntaxException, IOException {
        textureDirectory = textureDir;
        GltfModelReader reader = new GltfModelReader();
        GltfModelV2 model = (GltfModelV2) reader.read(Paths.get(resourceFile).toUri());
        textureModels = model.getTextureModels();

        SceneModel sceneModel = model.getSceneModels().get(0);

        translationAnimationSamplers = new HashMap<>();
        rotationAnimationSamplers = new HashMap<>();
        scaleAnimationSamplers = new HashMap<>();
        if(!model.getAnimationModels().isEmpty()) {
            if(model.getAnimationModels().size() != 1) throw new IOException(UNSUPPORTED_ANIMATION_MODEL);
            AnimationModel animationModel = model.getAnimationModels().get(0);
            animationModel.getChannels().forEach(channel -> {
                switch(channel.getPath()) {
                    case "translation" -> translationAnimationSamplers.put(channel.getNodeModel().getName(), channel.getSampler());
                    case "rotation" -> rotationAnimationSamplers.put(channel.getNodeModel().getName(), channel.getSampler());
                    case "scale" -> scaleAnimationSamplers.put(channel.getNodeModel().getName(), channel.getSampler());
                    case "weights" -> {}
                    default -> throw new RuntimeException(UNSUPPORTED_ANIMATION_MODEL);
                }
            });
        }

        List<ModelNode> children = new ArrayList<>();

        for ( NodeModel nodeModel: sceneModel.getNodeModels())
        {
            children.add(getChildModelNode(nodeModel));
        }

        return new Model(
                new ModelNode("OpenGLRootNode",
                        Collections.emptyList(),
                        children,
                        new Vector3f(),
                        new Quaternionf(0,0,0,1),
                        new Vector3f(1f),
                        new AnimationPlayer())
        );
    }

    private ModelNode getChildModelNode(NodeModel nodeModel) throws IOException {

        var translationAnimation = getAnimation(nodeModel, translationAnimationSamplers);
        var rotationAnimation = getRotationAnimation(nodeModel, rotationAnimationSamplers);
        var scaleAnimation = getAnimation(nodeModel, scaleAnimationSamplers);
        var animationPlayer = new AnimationPlayer();
        animationPlayer.put(
                "Hover",
                new Animation(translationAnimation, rotationAnimation, scaleAnimation)
                );
        animationPlayer.start("Hover", 0, true);

        List<Mesh> meshes = processMeshModels(nodeModel.getMeshModels());

        List<ModelNode> children = new ArrayList<>();
        for(NodeModel childrenNode : nodeModel.getChildren()) {
            children.add(getChildModelNode(childrenNode));
        }
        if(nodeModel.getMatrix() != null) // TODO: Matrices  get priority
        {
            float[] m = nodeModel.getMatrix();
            Vector3f localTranslation = new Vector3f(new float[]{m[12],m[13],m[14]});
            Vector3f localScale = new Vector3f(new float[]{0,0,0});
            Quaternionf localRotation = new Quaternionf();
            return new ModelNode(
                    nodeModel.getName(),
                    meshes,
                    children,
                    localTranslation,
                    localRotation,
                    localScale,
                    animationPlayer
            );
        }
        float[] translation = nodeModel.getTranslation() != null ? nodeModel.getTranslation(): new float[]{0, 0, 0};
        Vector3f localTranslation = new Vector3f(translation);
        float[] rotation = nodeModel.getRotation() != null ? nodeModel.getRotation(): new float[]{0, 0, 0, 1};
        float x = rotation[0];
        float y = rotation[1];
        float z = rotation[2];
        float w = rotation[3];
        Quaternionf localRotation = new Quaternionf(x, y, z, w);
        float[] scale = nodeModel.getScale() != null ? nodeModel.getScale(): new float[]{1, 1, 1};
        Vector3f localScale = new Vector3f(scale);

        return new ModelNode(
                nodeModel.getName(),
                meshes,
                children,
                localTranslation,
                localRotation,
                localScale,
                animationPlayer
        );
    }

    private List<Pair<Float, Vector3f>> getAnimation(NodeModel nodeModel, Map<String, AnimationModel.Sampler> samplerMap) throws IOException {
        if(!samplerMap.containsKey(nodeModel.getName())) return new ArrayList<>();
        var sampler = samplerMap.get(nodeModel.getName());
        if(sampler.getInterpolation() != AnimationModel.Interpolation.LINEAR) throw new IOException(UNSUPPORTED_ANIMATION_MODEL);
        AccessorFloatData animationTimeAccessor = (AccessorFloatData) sampler.getInput().getAccessorData();
        AccessorFloatData transformationAccessor = (AccessorFloatData) sampler.getOutput().getAccessorData();
        List<Pair<Float, Vector3f>> animation = new ArrayList<>();
        int n = animationTimeAccessor.getNumElements();
        for (int i = 0; i < n; i++) {
            float t = animationTimeAccessor.get(i, 0);
            float x = transformationAccessor.get(i, 0);
            float y = transformationAccessor.get(i, 1);
            float z = transformationAccessor.get(i, 2);
            animation.add(new Pair<>(t, new Vector3f(x, y, z)));
        }
        return animation;
    }

    private List<Pair<Float, Quaternionf>> getRotationAnimation(NodeModel nodeModel, Map<String, AnimationModel.Sampler> samplerMap) throws IOException {
        if(!samplerMap.containsKey(nodeModel.getName())) return new ArrayList<>();
        var sampler = samplerMap.get(nodeModel.getName());
        if(sampler.getInterpolation() != AnimationModel.Interpolation.LINEAR) throw new IOException(UNSUPPORTED_ANIMATION_MODEL);
        AccessorFloatData animationTimeAccessor = (AccessorFloatData) sampler.getInput().getAccessorData();
        AccessorFloatData transformationAccessor = (AccessorFloatData) sampler.getOutput().getAccessorData();
        List<Pair<Float, Quaternionf>> animation = new ArrayList<>();
        int n = animationTimeAccessor.getNumElements();
        for (int i = 0; i < n; i++) {
            float t = animationTimeAccessor.get(i, 0);
            float x = transformationAccessor.get(i, 0);
            float y = transformationAccessor.get(i, 1);
            float z = transformationAccessor.get(i, 2);
            float w = transformationAccessor.get(i, 3);
            animation.add(new Pair<>(t, new Quaternionf(x, y, z, w)));
        }
        return animation;
    }

    private List<Mesh> processMeshModels(List<MeshModel> meshModels) {
        List<Mesh> meshes = new ArrayList<>();
        for (MeshModel meshModel : meshModels)
        {
            for (MeshPrimitiveModel meshPrimitiveModel : meshModel.getMeshPrimitiveModels())
            {
                List<Vertex> vertices = new ArrayList<>();
                List<Vector3f> pos = getPosition(meshPrimitiveModel);
                List<Vector3f> nor = getNormals(meshPrimitiveModel);
                List<Vector2f> texc = getTextureCoords(meshPrimitiveModel);
                for(int i=0; i< pos.size(); i++)
                    vertices.add(new Vertex(pos.get(i), nor.get(i), texc.get(i)));

                List<Integer> ind = getIndices(meshPrimitiveModel);

                MaterialModel materialModel = meshPrimitiveModel.getMaterialModel();

                Material material = new Material(
                        new Vector3f(0.5f,0.5f,0.5f),
                        0.1f,//(float) materialModel.getValues().get("roughnessFactor"),
                        0.5f//(float) materialModel.getValues().get("metallicFactor")
                        );


                /*if(!materialModel.getValues().containsKey("baseColorTexture")) {
                    meshes.add(new Mesh(vertices, ind, List.of(), material));
                    continue;
                }*/
                if(materialModel.getValues().containsKey("baseColorTexture")) {
                    TextureModel textureModel = textureModels.get((Integer) materialModel.getValues().get("baseColorTexture")); // TODO Crash null when no texture on model
                    Texture texture = loadTexture(textureModel);
                    meshes.add(new Mesh(vertices, ind, List.of(texture), material));
                } else {
                    Texture texture = loadTexture("missing", Paths.get(System.getProperty("user.dir"),"assets", "missing.jpg"));
                    meshes.add(new Mesh(vertices, ind, List.of(texture), material));
                }
            }
        }
        return meshes;
    }

    private Texture loadTexture(TextureModel textureModel) {
        if(loadedTextures.containsKey(textureModel.getImageModel().getUri()))
            return loadedTextures.get(textureModel.getImageModel().getUri());

        ImageModel imageModel = textureModel.getImageModel();
        String s = imageModel.getUri();
        String fileName = s.substring(s.lastIndexOf('/') + 1);
        return loadTexture(textureModel.getImageModel().getUri(), Paths.get(textureDirectory, fileName));
    }

    private Texture loadTexture(String name, Path path) {
        loadingScreen.render("Loading " + name + "...");

        int[] w = new int[1];
        int[] h = new int[1];
        int[] components = new int[1];
        ByteBuffer image = stbi_load(path.toString(), w, h, components, 0);
        int format = GL_RGB;
        if(components[0] == 4) format = GL_RGBA;
        if(components[0] == 1) format = GL_BACK;
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w[0], h[0], 0, format, GL_UNSIGNED_BYTE, image);
        glGenerateMipmap(GL_TEXTURE_2D);
        stbi_image_free(image);

        Texture texture1 = new Texture(texture, "texture_diffuse");
        loadedTextures.put(name, texture1);
        return texture1;
    }

    private static List<Integer> getIndices(MeshPrimitiveModel meshPrimitiveModel) {
        List<Integer> list = new ArrayList<>();
        AccessorModel accessorModel = meshPrimitiveModel.getIndices();
        AccessorData accessorData = accessorModel.getAccessorData();
        if(accessorData.getComponentType() == short.class) // TODO: prettier
        {
            AccessorShortData accessorShortData =
                    (AccessorShortData) accessorData;
            int n = accessorShortData.getNumElements();
            for (int i = 0; i < n; i++) {
                int x = accessorShortData.get(i, 0);
                list.add(x);
            }
        }
        else if(accessorData.getComponentType() == int.class)
        {
            AccessorIntData accessorIntData =
                    (AccessorIntData) accessorData;
            int n = accessorIntData.getNumElements();
            for (int i = 0; i < n; i++) {
                int x = accessorIntData.get(i, 0);
                list.add(x);
            }
        }
        return list;
    }

    private static List<Vector3f> getPosition(MeshPrimitiveModel meshPrimitiveModel) {
        var list = new ArrayList<Vector3f>();
        AccessorModel accessorModel =
                meshPrimitiveModel.getAttributes().get("POSITION");
        AccessorData accessorData = accessorModel.getAccessorData();
        AccessorFloatData accessorFloatData =
                (AccessorFloatData) accessorData;
        int n = accessorFloatData.getNumElements();
        for (int i = 0; i < n; i++)
        {
            float x = accessorFloatData.get(i, 0);
            float y = accessorFloatData.get(i, 1);
            float z = accessorFloatData.get(i, 2);
            //System.out.println("Position " + i + " is " + x + " " + y + " " + z);
            list.add(new Vector3f(x, y, z));
        }
        return list;
    }

    private static List<Vector3f> getNormals(MeshPrimitiveModel meshPrimitiveModel) {
        var list = new ArrayList<Vector3f>();
        AccessorModel accessorModel =
                meshPrimitiveModel.getAttributes().get("NORMAL");
        AccessorData accessorData = accessorModel.getAccessorData();
        AccessorFloatData accessorFloatData =
                (AccessorFloatData) accessorData;
        int n = accessorFloatData.getNumElements();
        for (int i = 0; i < n; i++)
        {
            float x = accessorFloatData.get(i, 0);
            float y = accessorFloatData.get(i, 1);
            float z = accessorFloatData.get(i, 2);
            //System.out.println("Normal " + i + " is " + x + " " + y + " " + z);
            list.add(new Vector3f(x, y, z));
        }
        return list;
    }

    private static List<Vector2f> getTextureCoords(MeshPrimitiveModel meshPrimitiveModel) {
        var list = new ArrayList<Vector2f>();
        AccessorModel accessorModel =
                meshPrimitiveModel.getAttributes().get("TEXCOORD_0");
        AccessorData accessorData = accessorModel.getAccessorData();
        AccessorFloatData accessorFloatData =
                (AccessorFloatData) accessorData;
        int n = accessorFloatData.getNumElements();
        for (int i = 0; i < n; i++)
        {
            float x = accessorFloatData.get(i, 0);
            float y = accessorFloatData.get(i, 1);
            //System.out.println("TextureCoords " + i + " is " + x + " " + y );
            list.add(new Vector2f(x, y));
        }
        return list;
    }
}
