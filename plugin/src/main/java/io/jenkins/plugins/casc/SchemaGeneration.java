package io.jenkins.plugins.casc;

import io.jenkins.plugins.casc.impl.configurators.HeteroDescribableConfigurator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class SchemaGeneration {

    final static JSONObject schemaTemplateObject = new JSONObject()
        .put("$schema", "http://json-schema.org/draft-07/schema#")
        .put("description", "Jenkins Configuration as Code")
        .put("type", "object");

    public static JSONObject generateSchema() {

        /**
         * The initial template for the JSON Schema
         */
        JSONObject schemaObject = new JSONObject(schemaTemplateObject.toString());
        /**
         * This generates the schema for the root configurators
         * Iterates over the root elements and adds them to the schema.
         */
        schemaObject.put("properties", generateRootConfiguratorObject());
        /**
         * This generates the schema for the base configurators
         * Iterates over the base configurators and adds them to the schema.
         */
        JSONObject schemaConfiguratorObjects = new JSONObject();
        ConfigurationAsCode configurationAsCode = ConfigurationAsCode.get();
        for (Object configuratorObject : configurationAsCode.getConfigurators()) {
            if (configuratorObject instanceof BaseConfigurator) {
                BaseConfigurator baseConfigurator = (BaseConfigurator) configuratorObject;
                List<Attribute> baseConfigAttributeList = baseConfigurator.getAttributes();
                if (baseConfigAttributeList.size() == 0) {
                    schemaConfiguratorObjects
                        .put(((BaseConfigurator) configuratorObject).getTarget().getSimpleName().toLowerCase(),
                            new JSONObject()
                                .put("type", "object")
                                .put("properties", new JSONObject()));

                } else {
                    JSONObject attributeSchema = new JSONObject();
                    for (Attribute attribute : baseConfigAttributeList) {
                        if (attribute.multiple) {
                            generateMultipleAttributeSchema(attributeSchema, attribute);
                        } else {
                            if (attribute.type.isEnum()) {
                                generateEnumAttributeSchema(attributeSchema, attribute);
                            } else {
                                attributeSchema.put(attribute.getName(), generateNonEnumAttributeObject(attribute));
                                schemaConfiguratorObjects
                                    .put(((BaseConfigurator) configuratorObject).getTarget().getSimpleName().toLowerCase(),
                                        new JSONObject()
                                            .put("type", "object")
                                            .put("properties", attributeSchema));
                            }
                        }
                    }
                }
            } else if (configuratorObject instanceof HeteroDescribableConfigurator) {
                HeteroDescribableConfigurator heteroDescribableConfigurator = (HeteroDescribableConfigurator) configuratorObject;
                schemaConfiguratorObjects.put(heteroDescribableConfigurator.getTarget().getSimpleName().toLowerCase(),
                    generateHeteroDescribableConfigObject(heteroDescribableConfigurator));
                }
            }
        schemaObject.put("properties", schemaConfiguratorObjects);
        return schemaObject;
    }

    public static String writeJSONSchema() throws Exception{
        return generateSchema().toString(4);
    }

    private static JSONObject generateHeteroDescribableConfigObject(HeteroDescribableConfigurator heteroDescribableConfiguratorObject) {
        Map<String, Class> implementorsMap = heteroDescribableConfiguratorObject
            .getImplementors();
        JSONObject finalHeteroConfiguratorObject = new JSONObject();
        if (!implementorsMap.isEmpty()) {
            Iterator<Map.Entry<String, Class>> itr = implementorsMap.entrySet().iterator();

            JSONArray oneOfJsonArray = new JSONArray();
            while (itr.hasNext()) {
                Map.Entry<String, Class> entry = itr.next();
                JSONObject implementorObject = new JSONObject();
                implementorObject.put("properties",
                    new JSONObject().put(entry.getKey(), new JSONObject()
                        .put("$id", "#/definitions/" + entry.getValue().getName())));
                oneOfJsonArray.put(implementorObject);
            }

            finalHeteroConfiguratorObject.put("type", "object");
            finalHeteroConfiguratorObject.put("oneOf", oneOfJsonArray);
        }
            return finalHeteroConfiguratorObject;
    }

    private static JSONObject generateNonEnumAttributeObject(Attribute attribute) {
        JSONObject attributeType = new JSONObject();
        switch (attribute.type.getName()) {
            case "java.lang.String":
                attributeType.put("type", "string");
                break;

            case "int":
                attributeType.put("type", "integer");
                break;

            case "boolean":
                attributeType.put("type", "boolean");
                break;

            case "java.lang.Boolean":
                attributeType.put("type", "boolean");
                break;

            case "java.lang.Integer":
                attributeType.put("type", "integer");
                break;

            case "java.lang.Long":
                attributeType.put("type", "integer");
                break;

            default:
                attributeType.put("type", "object");
                attributeType.put("$id",
                    "#/definitions/" + attribute.type.getName());
                break;
        }
        return attributeType;
    }

    private static JSONObject generateRootConfiguratorObject() {
        JSONObject rootConfiguratorObject = new JSONObject();
        LinkedHashSet linkedHashSet = new LinkedHashSet<>(
            ConfigurationAsCode.get().getRootConfigurators());
        Iterator<RootElementConfigurator> i = linkedHashSet.iterator();
        while (i.hasNext()) {
            RootElementConfigurator rootElementConfigurator = i.next();
            rootConfiguratorObject
                .put(rootElementConfigurator.getName(), new JSONObject().put("type", "object"));
        }
        return rootConfiguratorObject;
    }

    private static void generateMultipleAttributeSchema(JSONObject attributeSchema, Attribute attribute) {
        if (attribute.type.getName().equals("java.lang.String")) {
            attributeSchema.put(attribute.getName(),
                new JSONObject()
                    .put("type", "string"));
        } else {
            attributeSchema.put(attribute.getName(),
                new JSONObject()
                    .put("type", "object")
                    .put("$id", "#/definitions/" + attribute.type.getName()));
        }
    }

    private static void generateEnumAttributeSchema(JSONObject attributeSchemaTemplate, Attribute attribute) {
        if (attribute.type.getEnumConstants().length == 0) {
            attributeSchemaTemplate.put(attribute.getName(),
                new JSONObject()
                    .put("type", "string"));
        } else {
            ArrayList<String> attributeList = new ArrayList<>();
            for (Object obj : attribute.type.getEnumConstants()) {
                attributeList.add(obj.toString());
            }
            attributeSchemaTemplate.put(attribute.getName(),
                new JSONObject()
                    .put("type", "string")
                    .put("enum", new JSONArray(attributeList)));
        }
    }
}

