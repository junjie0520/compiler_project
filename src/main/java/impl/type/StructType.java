package impl.type;
import impl.symbol.VariableSymbol;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StructType implements Type {
    private final String name;
    private final Map<String, VariableSymbol> members = new LinkedHashMap<>();

    public StructType(String name) { this.name = name; }
    public String getName() { return name; }

   //TODO: reject duplicate member names and preserve declaration order.
    public boolean defineMember(VariableSymbol member) {
        String memberName = member.getName();
        if (members.get(memberName) != null) {
            return false; // 已存在同名成员，拒绝定义
        }
        members.put(memberName, member);
        return true;
    }

    //TODO: search only this struct's member table.
    public VariableSymbol resolveMember(String name) {
        VariableSymbol result = members.get(name);
        return result;
    }

    public Collection<VariableSymbol> getMembers() {
        return Collections.unmodifiableCollection(members.values());
    }
    @Override public String prettyPrint() { return "struct " + name; }
}
