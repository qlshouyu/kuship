package cn.kuship.console.modules.grayrelease.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.CollectionType;

import java.util.List;

@Converter(autoApply = false)
public class ServiceMappingsConverter implements AttributeConverter<List<ServiceMappingEntry>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final CollectionType LIST_TYPE =
            MAPPER.getTypeFactory().constructCollectionType(List.class, ServiceMappingEntry.class);

    @Override
    public String convertToDatabaseColumn(List<ServiceMappingEntry> attribute) {
        if (attribute == null || attribute.isEmpty()) return "[]";
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public List<ServiceMappingEntry> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return List.of();
        return MAPPER.readValue(dbData, LIST_TYPE);
    }
}
