package impl.symbol;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Scope {
    protected final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Scope parent;

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope getParent() {
        return parent;
    }

    public Collection<Symbol> getLocalSymbols() {
        return Collections.unmodifiableCollection(symbols.values());
    }

    // 只在当前作用域判重；同名已存在返回 false 且不改内容。
    // 外层同名不影响登记（允许遮蔽）。
    public boolean define(Symbol symbol) {
        String name = symbol.getName();
        if (symbols.containsKey(name)) {
            return false;
        }
        symbols.put(name, symbol);
        return true;
    }

    // 只查当前作用域，找不到返回 null。
    public Symbol resolveLocal(String name) {
        return symbols.get(name);
    }

    // 从当前作用域向外逐级查找，返回首先找到的可见同名符号；均未找到返回 null。
    public Symbol resolve(String name) {
        Scope scope = this;
        while (scope != null) {
            Symbol s = scope.symbols.get(name);
            if (s != null) {
                return s;
            }
            scope = scope.parent;
        }
        return null;
    }
}