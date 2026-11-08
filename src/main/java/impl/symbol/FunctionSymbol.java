package impl.symbol;

import impl.type.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// A function is BOTH a named Symbol (in global scope) and a Scope
// (containing parameters and top-level locals in its body).
public final class FunctionSymbol extends Scope implements Symbol {
    private final String name;
    private final Type returnType;
    private final List<VariableSymbol> parameters = new ArrayList<>();

    public FunctionSymbol(String name, Type returnType, Scope parent) {
        super(parent);
        this.name = name;
        this.returnType = returnType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return returnType;
    }

    public List<VariableSymbol> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    // 形参同时登记到本函数作用域与有序参数列表，两处保存同一对象。
    // 若当前函数作用域中已有同名普通符号，返回 false 且不改变任何内容。
    public boolean addParameter(VariableSymbol parameter) {
        if (resolveLocal(parameter.getName()) != null) {
            return false;
        }
        define(parameter);
        parameters.add(parameter);
        return true;
    }
}