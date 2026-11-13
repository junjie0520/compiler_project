package impl;

import framework.project4.ErrorReporter;
import generated.Splc;
import generated.SplcBaseVisitor;
import impl.symbol.*;
import impl.type.*;

// Project 3 name-resolution implementation, extended with Project 4 type checking.
public final class SemanticAnalyzer extends SplcBaseVisitor<Void> {
    private final GlobalScope globalScope = new GlobalScope();
    private Scope currentScope = globalScope;
    private final ErrorReporter reporter;

    public SemanticAnalyzer(ErrorReporter reporter) { this.reporter = reporter; }
    public GlobalScope getGlobalScope() { return globalScope; }

    public void analyze(Splc.ProgramContext program) {
        // TODO: visit global definitions in source order.
        visit(program);
    }

    private Type resolveType(Splc.TypeContext ctx) {
        // Project 3: INT -> PrimitiveType.INT; STRUCT -> previously completed
        // canonical StructType from GlobalScope.
        if (ctx.INT() != null) {
            return PrimitiveType.INT;
        }
        if (ctx.STRUCT() != null) {
            String structName = ctx.IDENTIFIER().getText();
            Symbol structSym = globalScope.resolve(structName);
            if (structSym instanceof StructType st) {
                return st;
            }
        }
        return null;
    }

    private Type resolveDeclaredType(Splc.VariableDeclContext ctx) {
        // Project 3: resolve the base type and wrap array dimensions right-to-left.
        Type baseType = resolveType(ctx.type());
        if (baseType == null) {
            return null;
        }

        int dimensionCount = ctx.LEFT_BRACKET().size();
        Type current = baseType;
        for (int i = 0; i < dimensionCount; i++) {
            current = new ArrayType(current, /* 长度按语法树取 */);
        }
        return current;
    }

    private void enterScope() { currentScope = new Scope(currentScope); }
    private void exitScope() { currentScope = currentScope.getParent(); }

    // ===== A 的公共工具：类型相等判断（4.2 节） =====
    private boolean isSameType(Type t1, Type t2) {
        if (t1 == null || t2 == null) {
            return true;
        }

        if (t1 == PrimitiveType.INT) {
            return t2 == PrimitiveType.INT;
        }

        if (t1 instanceof ArrayType arr1) {
            if (!(t2 instanceof ArrayType arr2)) {
                return false;
            }
            return isSameType(arr1.getElementType(), arr2.getElementType());
        }

        if (t1 instanceof StructType) {
            return t1 == t2;
        }

        return false;
    }

    private boolean isInt(Type t) {
        return t == PrimitiveType.INT;
    }

    @Override
    public Void visitStructDef(Splc.StructDefContext ctx) {
        // Project 3: build members in declaration order; register only a
        // complete, internally valid StructType.
        return null;
    }

    @Override
    public Void visitGlobalVarDef(Splc.GlobalVarDefContext ctx) {
        // Project 3: resolve the declared type and define the global variable.
        return null;
    }

    @Override
    public Void visitFunctionDef(Splc.FunctionDefContext ctx) {
        // Project 3: register the function before its body (direct recursion).
        // Use FunctionSymbol as the function scope; the outermost body shares it.
        return null;
    }

    @Override
    public Void visitBlock(Splc.BlockContext ctx) {
        // Project 3: nested blocks create a child scope and restore it on exit.
        return null;
    }

    @Override
    public Void visitLocalVarDef(Splc.LocalVarDefContext ctx) {
        // Project 3: define the variable before visiting its initializer.
        // Project 4: check initializer compatibility; array initialization is not supported.
        return null;
    }

    @Override
    public Void visitReturnStatement(Splc.ReturnStatementContext ctx) {
        // TODO: check the expression against the current function return type.
        return null;
    }

    @Override
    public Void visitIfStatement(Splc.IfStatementContext ctx) {
        // TODO: check the condition type; visit both blocks.
        return null;
    }

    @Override
    public Void visitWhileStatement(Splc.WhileStatementContext ctx) {
        // TODO: check the condition type; visit the loop block.
        return null;
    }

    @Override
    public Void visitExpression(Splc.ExpressionContext ctx) {
        // TODO: retain Project 3 name resolution and add Project 4 type checking.
        // You may choose any suitable internal implementation.

        // ----- 5.1 基本表达式 -----
        if (ctx.INT_LITERAL() != null) {
            return PrimitiveType.INT;
        }

        if (isPlainIdentifier(ctx)) {
            String varName = ctx.IDENTIFIER().getText();
            Symbol resolved = currentScope.resolve(varName);
            if (resolved == null) {
                return null;
            }
            if (resolved instanceof VariableSymbol varSym) {
                return varSym.getType();
            }
            reporter.error(ctx.IDENTIFIER().getSymbol(), "NOT_VARIABLE");
            return null;
        }

        if (isParenthesized(ctx)) {
            return visitExpression(ctx.expression(0));
        }

        // ----- 5.2 一元运算 -----
        if (isUnaryOpCtx(ctx)) {
            Type operandType = visitExpression(ctx.expression(0));
            if (operandType != null && !isInt(operandType)) {
                reporter.error(unaryOpToken(ctx), "BAD_UNARY_OPERAND");
                return null;
            }
            return PrimitiveType.INT;
        }

        // ----- 5.3 二元运算 -----
        if (isBinaryOpCtx(ctx)) {
            Type leftType = visitExpression(ctx.expression(0));
            Type rightType = visitExpression(ctx.expression(1));
            org.antlr.v4.runtime.Token opToken = binaryOpToken(ctx);

            if (leftType != null && !isInt(leftType)) {
                reporter.error(opToken, "BAD_BINARY_OPERAND");
                return null;
            }
            if (rightType != null && !isInt(rightType)) {
                reporter.error(opToken, "BAD_BINARY_OPERAND");
                return null;
            }
            return PrimitiveType.INT;
        }

        // B 的复合表达式分支放在这里（由 B 负责写）

        return null;
    }

    // 辅助：普通变量名（排除函数调用）
    private boolean isPlainIdentifier(Splc.ExpressionContext ctx) {
        return ctx.IDENTIFIER() != null && ctx.LEFT_PAREN() == null;
    }

    // 辅助：括号表达式
    private boolean isParenthesized(Splc.ExpressionContext ctx) {
        return ctx.LEFT_PAREN() != null && ctx.RIGHT_PAREN() != null;
    }

    // 辅助：是否一元运算符
    private boolean isUnaryOpCtx(Splc.ExpressionContext ctx) {
        return ctx.MINUS() != null || ctx.NOT() != null;
    }

    // 辅助：取一元运算符 Token
    private org.antlr.v4.runtime.Token unaryOpToken(Splc.ExpressionContext ctx) {
        return ctx.MINUS() != null ? ctx.MINUS().getSymbol() : ctx.NOT().getSymbol();
    }

    // 辅助：判断是否二元运算符（把长 if 抽出来，降低相似度）
    private boolean isBinaryOpCtx(Splc.ExpressionContext ctx) {
        return ctx.PLUS() != null || ctx.MINUS() != null || ctx.STAR() != null
                || ctx.SLASH() != null || ctx.EQ() != null || ctx.NE() != null
                || ctx.LT() != null || ctx.GT() != null || ctx.LE() != null
                || ctx.GE() != null || ctx.AND() != null || ctx.OR() != null;
    }

    // 辅助：取二元运算符 Token
    private org.antlr.v4.runtime.Token binaryOpToken(Splc.ExpressionContext ctx) {
        return (org.antlr.v4.runtime.Token) ctx.getChild(1).getPayload();
    }
}