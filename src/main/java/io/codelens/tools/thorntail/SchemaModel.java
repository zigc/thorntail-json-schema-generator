package io.codelens.tools.thorntail;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static io.codelens.tools.thorntail.Utils.*;

public class SchemaModel {

    private static final String PROJECT_PREFIX = "swarm";
    private static final String ROOT_TITLE = "Thorntail configuration";

    private Node root;

    private SchemaModel(Set<Class<? extends ModelBuilder>> modelBuilderClasses) {
        this.root = Node.createNode(PROJECT_PREFIX, ROOT_TITLE, NodeType.OBJECT);
        instantiate(modelBuilderClasses).forEach(this::applyModelBuilder);
    }

    private void applyModelBuilder(ModelBuilder modelBuilder) {
        modelBuilder.build(this);
    }

    public void addPath(String path, String description) {
        addPath(path, description, String.class);
    }

    public void addPath(String path, String description, Field field) {
        addPath(path, description, getGenericType(field));
    }

    public void addPath(String path, String description, Class clz) {
        NodeType type = findJsonType(clz);
        Node node = createOrGetNodeByPath(path);
        node.setDescription(description);
        node.setJavaType(type);
    }

    public Node createOrGetNodeByPath(String path) {
        Node currentNode = new Node();
        currentNode.getChildren().add(root);

        for (String pathPart : path.split("\\.")) {
            Optional<Node> node = currentNode.getChildByName(pathPart);
            if (node.isPresent()) {
                currentNode = node.get();
            } else {
                Node newNode = Node.createNode(pathPart, null, NodeType.OBJECT);
                currentNode.appendChild(newNode);
                currentNode = newNode;
            }
        }

        return currentNode;
    }

    public JsonObject generateSchema(boolean writeDescription) {
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        // draft-04 -> for yaml validation
        objectBuilder.
                add(Node.SCHEMA, "http://json-schema.org/draft-04/schema#").
                add(Node.ID, "https://thorntail.io/schema").
                add(Node.TITLE, "Thorntail Configuration (Autogenerated)").
                add(Node.TYPE, NodeType.OBJECT.getJsonString()).
                add(Node.ADDITIONAL_PROPERTIES, Boolean.FALSE).
                add(Node.PROPERTIES, root.toJsonObjectBuilder(writeDescription));

        return objectBuilder.build();
    }

    private void addProperties(Properties properties, Node node, boolean insertType) {
        String description = node.getDescription();
        description = description == null ? "(not documented yet)" : trimSpecialsChars(description);
        
        if (insertType) {
            description = "(" + node.getJavaType().getJsonString() + ")" + (description.isEmpty() ? "" : " ") + description;
        }
        
        if (!node.isKey()) {
            properties.put(node.getPath(), description);
        }
        
        if (!node.getChildren().isEmpty()) {
            node.getChildren().forEach(childNode -> addProperties(properties, childNode, insertType));
        }
    }
    
    public Properties generateProperties(boolean insertType) {
        Properties properties = new Properties();
        addProperties(properties, root, insertType);
        return properties;
    }

    public static SchemaModel of(Set<Class<? extends ModelBuilder>> modelBuilders) {
        return new SchemaModel(modelBuilders);
    }

}