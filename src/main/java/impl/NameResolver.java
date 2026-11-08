package impl;

import framework.project3.ErrorReporter;
import generated.Splc;
import generated.SplcBaseVisitor;
import impl.symbol.*;
import impl.type.*;
import org.antlr.v4.runtime.Token;

import java.util.List;

// Student's main semantic-analysis implementation.
public final class NameResolver extends SplcBaseVisitor<Void> {
    private final GlobalScope globalScope = new GlobalScope();
    private Scope currentScope = globalScope;
    private final ErrorReporter reporter;

    public NameResolver(ErrorReporter reporter) { this.reporter = reporter; }
    public GlobalScope getGlobalScope() { return globalScope; }

    public void analyze(Splc.ProgramContext program) {
        // Visit global definitions IN SOURCE ORDER.
        visit(program);
    }

    // ---------- 类型解析 ----------

    private Type resolveType(Splc.TypeContext ctx) {
        if (ctx == null) return null;
        if (ctx.INT() != null) {
            return PrimitiveType.INT;
        }
        // STRUCT Identifier
        Token nameTok = ctx.Identifier().getSymbol();
        StructType st = globalScope.resolveStruct(nameTok.getText());
        if (st == null) {
            reporter.undefinedStruct(nameTok);
        }
        return st;
    }

    private Type resolveDeclaredType(Splc.VariableDeclContext ctx) {
        Type base = resolveType(ctx.type());
        if (base == null) return null;
        List<Splc.ArrayDimensionContext> dims = ctx.arrayDimension();
        Type type = base;
        // 从右到左包装：int a[2][3] -> ArrayType(ArrayType(INT, 3), 2)
        for (int i = dims.size() - 1; i >= 0; i--) {
            int len = Integer.parseInt(dims.get(i).Number().getText());
            type = new ArrayType(type, len);   // 若构造器是 (len, type) 则对调
        }
        return type;
    }

    private void enterScope() { currentScope = new Scope(currentScope); }
    private void exitScope() { currentScope = currentScope.getParent(); }

    // ---------- 声明 ----------

    @Override
    public Void visitStructDef(Splc.StructDefContext ctx) {
        Token nameTok = ctx.Identifier().getSymbol();
        String name = nameTok.getText();

        // 结构体重名：先查全局类型表
        if (globalScope.resolveStruct(name) != null) {
            reporter.redefinedStruct(nameTok);
            return null;
        }

        StructType struct = new StructType(name);
        boolean ok = true;

        for (Splc.FieldDefContext f : ctx.fieldDef()) {
            Splc.VariableDeclContext decl = f.variableDecl();
            Token fieldTok = decl.Identifier().getSymbol();
            Type fieldType = resolveDeclaredType(decl);
            if (fieldType == null) {
                ok = false;
                continue;
            }
            VariableSymbol field = new VariableSymbol(fieldTok.getText(), fieldType);
            if (!struct.defineMember(field)) {
                reporter.duplicateMember(fieldTok);
                ok = false;
            }
        }

        // 所有字段合法才登记
        if (ok) {
            globalScope.defineStruct(struct);
        }
        return null;
    }

    @Override
    public Void visitGlobalVarDef(Splc.GlobalVarDefContext ctx) {
        Splc.VariableDeclContext decl = ctx.variableDecl();
        Token nameTok = decl.Identifier().getSymbol();
        Type type = resolveDeclaredType(decl);
        if (type == null) {
            return null;
        }
        VariableSymbol var = new VariableSymbol(nameTok.getText(), type);
        if (!globalScope.define(var)) {
            reporter.redefinedSymbol(nameTok);
        }
        return null;
    }

    @Override
    public Void visitFunctionDef(Splc.FunctionDefContext ctx) {
        Token nameTok = ctx.Identifier().getSymbol();
        Type returnType = resolveType(ctx.type());
        if (returnType == null) {
            // 返回类型失败：不登记函数
            return null;
        }

        FunctionSymbol func = new FunctionSymbol(nameTok.getText(), returnType, globalScope);
        if (!globalScope.define(func)) {
            // 函数重名：保留原定义，不处理函数体
            reporter.redefinedSymbol(nameTok);
            return null;
        }

        // 进入函数作用域（形参与最外层局部共用）
        Scope saved = currentScope;
        currentScope = func;

        // 形参
        if (ctx.parameterList() != null) {
            for (Splc.ParameterContext p : ctx.parameterList().parameter()) {
                Splc.VariableDeclContext decl = p.variableDecl();
                Token pTok = decl.Identifier().getSymbol();
                Type pType = resolveDeclaredType(decl);
                if (pType == null) {
                    continue;
                }
                VariableSymbol param = new VariableSymbol(pTok.getText(), pType);
                if (!func.addParameter(param)) {
                    reporter.redefinedSymbol(pTok);
                }
            }
        }

        // 函数体最外层：复用函数作用域，直接遍历 statement，不调 visitBlock
        for (Splc.StatementContext s : ctx.block().statement()) {
            visit(s);
        }

        currentScope = saved;
        return null;
    }

    @Override
    public Void visitBlock(Splc.BlockContext ctx) {
        // 仅嵌套块走这里；函数体最外层已由 visitFunctionDef 处理
        enterScope();
        try {
            for (Splc.StatementContext s : ctx.statement()) {
                visit(s);
            }
        } finally {
            exitScope();
        }
        return null;
    }

    @Override
    public Void visitLocalVarDef(Splc.LocalVarDefContext ctx) {
        Splc.VariableDeclContext decl = ctx.variableDecl();
        Token nameTok = decl.Identifier().getSymbol();
        Type type = resolveDeclaredType(decl);
        if (type == null) {
            // 类型失败：不登记，但仍解析初始化表达式中的名称
            if (ctx.expression() != null) {
                visit(ctx.expression());
            }
            return null;
        }
        VariableSymbol var = new VariableSymbol(nameTok.getText(), type);
        if (!currentScope.define(var)) {
            reporter.redefinedSymbol(nameTok);
        }
        // 先登记后解析初始化：int x = x; 合法
        if (ctx.expression() != null) {
            visit(ctx.expression());
        }
        return null;
    }

    // ---------- 表达式 ----------

    @Override
    public Void visitExpression(Splc.ExpressionContext ctx) {
        List<Splc.ExpressionContext> subs = ctx.expression();

        // ① 成员访问 expression DOT Identifier —— 必须最先判断！
        //    只解析左侧，点号后的 Identifier 是成员名，不查普通符号表
        if (ctx.DOT() != null) {
            visit(subs.get(0));
            return null;
        }

        // ② 普通 Identifier（无括号）
        if (ctx.Identifier() != null && ctx.LPAREN() == null) {
            Token tok = ctx.Identifier().getSymbol();
            if (currentScope.resolve(tok.getText()) == null) {
                reporter.undeclaredIdentifier(tok);
            }
            return null;
        }

        // ③ 调用 Identifier LPAREN argumentList? RPAREN
        if (ctx.Identifier() != null && ctx.LPAREN() != null) {
            Token tok = ctx.Identifier().getSymbol();
            if (currentScope.resolve(tok.getText()) == null) {
                reporter.undeclaredIdentifier(tok);
            }
            if (ctx.argumentList() != null) {
                for (Splc.ExpressionContext a : ctx.argumentList().expression()) {
                    visit(a);
                }
            }
            return null;
        }

        // ④ 数组下标 / 一元 / 二元 / 括号：递归所有子表达式
        for (Splc.ExpressionContext s : subs) {
            visit(s);
        }
        return null;
    }
}