package impl.symbol;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class Scope {
    protected final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Scope parent;

    public Scope(Scope parent) { this.parent = parent; }
    public Scope getParent() { return parent; }
    public Collection<Symbol> getLocalSymbols() {
        return Collections.unmodifiableCollection(symbols.values());
    }

    // TODO: check only this scope; shadowing in child scopes is allowed.
    public boolean define(Symbol symbol) {
        String symbolName = symbol.getName();
        if (symbols.get(symbolName) != null) {
            return false; // 当前作用域已有同名符号，拒绝重复定义
        }
        symbols.put(symbolName, symbol);
        return true;
    }

    // TODO: look up only in this scope.
    public Symbol resolveLocal(String name) {
        Symbol found = symbols.get(name);
        return found;
    }

    // TODO: search this scope, then parent scopes.
    public Symbol resolve(String name) {
        Symbol found = resolveLocal(name);
        if (found != null) {
            return found;
        }
        Scope up = parent;
        if (up == null) {
            return null;
        }
        return up.resolve(name);
    }
}
