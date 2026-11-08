package impl.symbol;

import impl.type.StructType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GlobalScope extends Scope {
    private final Map<String, StructType> structTypes = new LinkedHashMap<>();

    public GlobalScope() { super(null); }

    public Collection<StructType> getStructTypes() {
        return Collections.unmodifiableCollection(structTypes.values());
    }

    // 只登记已完整定义的结构体；同名已存在返回 false 且不改内容。
    public boolean defineStruct(StructType type) {
        String name = type.getName();
        if (structTypes.containsKey(name)) {
            return false;
        }
        structTypes.put(name, type);
        return true;
    }

    // 只查全局类型表，不碰 Scope.resolve。
    public StructType resolveStruct(String name) {
        return structTypes.get(name);
    }
}