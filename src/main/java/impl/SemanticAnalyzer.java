package impl;

import framework.project4.ErrorReporter;
import generated.Splc;
import generated.SplcBaseVisitor;
import impl.symbol.*;
import impl.type.*;
import org.antlr.v4.runtime.Token;

import java.util.List;

// Project 3 name-resolution implementation, extended with Project 4 type checking.
public final class SemanticAnalyzer extends SplcBaseVisitor<Type> {
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
            Token nameToken = ctx.Identifier().getSymbol();
            StructType structType = globalScope.resolveStruct(nameToken.getText());
            if (structType == null) {
                reporter.undefinedStruct(nameToken);
            }
            return structType;
        }
        return null;
    }

    private Type resolveDeclaredType(Splc.VariableDeclContext ctx) {
        // Project 3: resolve the base type and wrap array dimensions right-to-left.
        Type baseType = resolveType(ctx.type());
        if (baseType == null) {
            return null;
        }

        List<Splc.ArrayDimensionContext> dimensions = ctx.arrayDimension();
        Type current = baseType;
        // int a[2][3] -> ArrayType(ArrayType(int, 3), 2)
        for (int i = dimensions.size() - 1; i >= 0; i--) {
            int length = Integer.parseInt(dimensions.get(i).Number().getText());
            current = new ArrayType(current, length);
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
    public Type visitStructDef(Splc.StructDefContext ctx) {
        // Project 3: build members in declaration order; register only a
        // complete, internally valid StructType.
        return null;
    }

    @Override
    public Type visitGlobalVarDef(Splc.GlobalVarDefContext ctx) {
        // Project 3: resolve the declared type and define the global variable.
        return null;
    }

    @Override
    public Type visitFunctionDef(Splc.FunctionDefContext ctx) {
        // Project 3: register the function before its body (direct recursion).
        // Use FunctionSymbol as the function scope; the outermost body shares it.
        return null;
    }

    @Override
    public Type visitBlock(Splc.BlockContext ctx) {
        // Project 3: nested blocks create a child scope and restore it on exit.
        return null;
    }

    @Override
    public Type visitLocalVarDef(Splc.LocalVarDefContext ctx) {
        // Project 3: define the variable before visiting its initializer.
        // Project 4: check initializer compatibility; array initialization is not supported.
        return null;
    }

    @Override
    public Type visitReturnStatement(Splc.ReturnStatementContext ctx) {
        // TODO: check the expression against the current function return type.
        return null;
    }

    @Override
    public Type visitIfStatement(Splc.IfStatementContext ctx) {
        // TODO: check the condition type; visit both blocks.
        return null;
    }

    @Override
    public Type visitWhileStatement(Splc.WhileStatementContext ctx) {
        // TODO: check the condition type; visit the loop block.
        return null;
    }

    @Override
    public Type visitExpression(Splc.ExpressionContext ctx) {
        // TODO: retain Project 3 name resolution and add Project 4 type checking.
        // You may choose any suitable internal implementation.

        // expression DOT Identifier
        // 必须先于裸 Identifier 判断：p.x 的成员名 x 也是 Identifier 子节点
        if (ctx.DOT() != null) {
            Type baseType = visitExpression(ctx.expression(0));
            Token memberToken = ctx.Identifier().getSymbol();
            if (baseType == null) {
                return null; // 类型未知：静默传播，避免级联报错
            }
            if (!(baseType instanceof StructType structType)) {
                reporter.expectedStruct(ctx.expression(0).getStart());
                return null;
            }
            VariableSymbol member = structType.resolveMember(memberToken.getText());
            if (member == null) {
                reporter.unknownMember(memberToken);
                return null;
            }
            return member.getType();
        }

        //expression LBRACK expression RBRACK 
        //两个下标类错误的报错 token 都是左方括号 
        if (ctx.LBRACK() != null) {
            Token bracketToken = ctx.LBRACK().getSymbol();
            Type baseType = visitExpression(ctx.expression(0));
            Type indexType = visitExpression(ctx.expression(1));

            if (indexType != null && !isInt(indexType)) {
                reporter.badIndexType(bracketToken);
            }
            if (baseType == null) {
                return null;
            }
            if (!(baseType instanceof ArrayType arrayType)) {
                reporter.expectedArray(bracketToken);
                return null;
            }
            return arrayType.getElementType();
        }

        // ===== B：赋值 expression ASSIGN expression =====
        // 结果类型等于左侧左值类型，支持 a = b = c 的右结合链
        if (ctx.ASSIGN() != null) {
            Token assignToken = ctx.ASSIGN().getSymbol();
            Splc.ExpressionContext lhs = ctx.expression(0);
            Type lhsType = visitExpression(lhs);
            Type rhsType = visitExpression(ctx.expression(1));

            if (!isAssignable(lhs)) {
                reporter.invalidAssignmentTarget(assignToken);
            } else if (lhsType != null && rhsType != null && !isSameType(lhsType, rhsType)) {
                reporter.typeMismatch(assignToken);
            }
            return lhsType != null ? lhsType : rhsType;
        }

        // ----- 5.1 基本表达式 -----
        if (ctx.Number() != null) {
            return PrimitiveType.INT;
        }

        if (isPlainIdentifier(ctx)) {
            String varName = ctx.Identifier().getText();
            Symbol resolved = currentScope.resolve(varName);
            if (resolved == null) {
                return null;
            }
            if (resolved instanceof VariableSymbol varSym) {
                return varSym.getType();
            }
            reporter.notVariable(ctx.Identifier().getSymbol());
            return null;
        }

        if (isParenthesized(ctx)) {
            return visitExpression(ctx.expression(0));
        }

        // ----- 5.2 一元运算 -----
        if (isUnaryOpCtx(ctx)) {
            Type operandType = visitExpression(ctx.expression(0));
            if (operandType != null && !isInt(operandType)) {
                reporter.badUnaryOperand(unaryOpToken(ctx));
                return null;
            }
            return PrimitiveType.INT;
        }

        // ----- 5.3 二元运算 -----
        if (isBinaryOpCtx(ctx)) {
            Type leftType = visitExpression(ctx.expression(0));
            Type rightType = visitExpression(ctx.expression(1));
            Token opToken = binaryOpToken(ctx);

            if (leftType != null && !isInt(leftType)) {
                reporter.badBinaryOperand(opToken);
                return null;
            }
            if (rightType != null && !isInt(rightType)) {
                reporter.badBinaryOperand(opToken);
                return null;
            }
            return PrimitiveType.INT;
        }

        // B 的复合表达式分支已前置到 visitExpression 开头

        return null;
    }

    // 辅助：合法赋值目标（变量 / 数组元素 / 结构体成员，递归检查基底）
    private boolean isAssignable(Splc.ExpressionContext expr) {
        if (expr.DOT() != null) {
            return isAssignable(expr.expression(0));
        }
        if (expr.LBRACK() != null) {
            return isAssignable(expr.expression(0));
        }
        return expr.Identifier() != null && expr.LPAREN() == null;
    }

    // 辅助：普通变量名（排除函数调用）
    private boolean isPlainIdentifier(Splc.ExpressionContext ctx) {
        return ctx.Identifier() != null && ctx.LPAREN() == null;
    }

    // 辅助：括号表达式
    private boolean isParenthesized(Splc.ExpressionContext ctx) {
        return ctx.LPAREN() != null && ctx.RPAREN() != null;
    }

    // 辅助：是否一元运算符
    private boolean isUnaryOpCtx(Splc.ExpressionContext ctx) {
        return ctx.MINUS() != null || ctx.NOT() != null;
    }

    // 辅助：取一元运算符 Token
    private Token unaryOpToken(Splc.ExpressionContext ctx) {
        return ctx.MINUS() != null ? ctx.MINUS().getSymbol() : ctx.NOT().getSymbol();
    }

    // 辅助：判断是否二元运算符（把长 if 抽出来，降低相似度）
    private boolean isBinaryOpCtx(Splc.ExpressionContext ctx) {
        return ctx.PLUS() != null || ctx.MINUS() != null || ctx.MUL() != null
                || ctx.DIV() != null || ctx.EQ() != null || ctx.NEQ() != null
                || ctx.LT() != null || ctx.GT() != null || ctx.LE() != null
                || ctx.GE() != null || ctx.AND() != null || ctx.OR() != null;
    }

    // 辅助：取二元运算符 Token
    private Token binaryOpToken(Splc.ExpressionContext ctx) {
        return (Token) ctx.getChild(1).getPayload();
    }
}