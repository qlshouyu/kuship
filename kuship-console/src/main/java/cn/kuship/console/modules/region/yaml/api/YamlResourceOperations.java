package cn.kuship.console.modules.region.yaml.api;

import java.util.Map;

/** rainbond `yaml_resource_*`：3 method。 */
public interface YamlResourceOperations {

    String IMPLEMENTING_CHANGE = "migrate-console-app-import-export";

    /** GET /v2/cluster/yaml_resource_name?eid=（GET with body） */
    default Map<String, Object> yamlResourceName(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** GET /v2/cluster/yaml_resource_detailed?eid=（GET with body） */
    default Map<String, Object> yamlResourceDetailed(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }

    /** POST /v2/cluster/yaml_resource_import?eid= */
    default Map<String, Object> yamlResourceImport(String enterpriseId, String regionName, Map<String, Object> body) {
        throw new UnsupportedOperationException(IMPLEMENTING_CHANGE);
    }
}
