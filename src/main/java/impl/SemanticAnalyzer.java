package impl;

import framework.project4.ErrorReporter;
import generated.Splc;
import generated.SplcBaseVisitor;
import impl.symbol.*;
import impl.type.*;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
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
            // 数组：元素类型相同且长度相同
            return arr1.getLength() == arr2.getLength()
                    && isSameType(arr1.getElementType(), arr2.getElementType());
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
        Token nameToken = ctx.Identifier().getSymbol();
        String name = nameToken.getText();
        List<VariableSymbol> members = new ArrayList<>();
        boolean allValid = true;
        if (globalScope.resolveStruct(name) != null) {
            reporter.redefinedStruct(nameToken);
            allValid = false;
        }

        // structDef : STRUCT Identifier LBRACE fieldDef+ RBRACE SEMI
        for (Splc.FieldDefContext field : ctx.fieldDef()) {
            Splc.VariableDeclContext memberDecl = field.variableDecl();
            Token memberToken = memberDecl.Identifier().getSymbol();
            Type memberType = resolveDeclaredType(memberDecl);
            if (memberType == null) {
                allValid = false;
                continue;
            }
            boolean duplicated = false;
            for (VariableSymbol existing : members) {
                if (existing.getName().equals(memberToken.getText())) {
                    reporter.duplicateMember(memberToken);
                    duplicated = true;
                    allValid = false;
                    break;
                }
            }
            if (!duplicated) {
                members.add(new VariableSymbol(memberToken.getText(), memberType));
            }
        }

        // 仅在全部成员合法时调用 defineStruct
        if (allValid) {
            StructType structType = new StructType(name);
            for (VariableSymbol m : members) {
                structType.defineMember(m); // 若实际方法名为 addMember/putMember，请替换
            }
            globalScope.defineStruct(structType);
        }
        return null;
    }

    @Override
    public Type visitGlobalVarDef(Splc.GlobalVarDefContext ctx) {
        // Project 3: resolve the declared type and define the global variable.
        Token nameToken = ctx.variableDecl().Identifier().getSymbol();
        Type declaredType = resolveDeclaredType(ctx.variableDecl());
        if (declaredType == null) {
            return null;
        }
        if (globalScope.resolve(nameToken.getText()) != null) {
            reporter.redefinedSymbol(nameToken);
        } else {
            globalScope.define(new VariableSymbol(nameToken.getText(), declaredType));
        }
        return null;
    }

    @Override
    public Type visitFunctionDef(Splc.FunctionDefContext ctx) {
        // Project 3: register the function before its body (direct recursion).
        // Use FunctionSymbol as the function scope; the outermost body shares it.
        Token nameToken = ctx.Identifier().getSymbol();
        Type returnType = resolveType(ctx.type());
        // FunctionSymbol(String, Type, Scope)：第三个参数为父作用域
        FunctionSymbol function = new FunctionSymbol(nameToken.getText(), returnType, currentScope);

        if (globalScope.resolve(nameToken.getText()) != null) {
            reporter.redefinedSymbol(nameToken);
        } else {
            globalScope.define(function);
        }

        Scope savedScope = currentScope;
        currentScope = function;
        try {
            // 依次处理形参：functionDef -> parameterList? -> parameter -> variableDecl
            if (ctx.parameterList() != null) {
                for (Splc.ParameterContext param : ctx.parameterList().parameter()) {
                    Splc.VariableDeclContext paramDecl = param.variableDecl();
                    Token paramToken = paramDecl.Identifier().getSymbol();
                    Type paramType = resolveDeclaredType(paramDecl);
                    VariableSymbol paramSym = new VariableSymbol(paramToken.getText(), paramType);

                    // 只查当前函数作用域，避免与外层同名误报（shadowing 合法）
                    if (function.resolveLocal(paramToken.getText()) != null) {
                        reporter.redefinedSymbol(paramToken);
                    } else if (!function.addParameter(paramSym)) {
                        // addParameter 内部 define 失败（理论上不会走到，因已判重）
                        reporter.redefinedSymbol(paramToken);
                    }
                }
            }
            // 函数体是 block：最外层不新建作用域，直接遍历其 statement
            for (Splc.StatementContext stmt : ctx.block().statement()) {
                visit(stmt);
            }
        } finally {
            currentScope = savedScope;
        }
        return null;
    }

    @Override
    public Type visitBlock(Splc.BlockContext ctx) {
        // Project 3: nested blocks create a child scope and restore it on exit.
        enterScope();
        try {
            for (Splc.StatementContext stmt : ctx.statement()) {
                visit(stmt);
            }
        } finally {
            exitScope();
        }
        return null;
    }

    @Override
    public Type visitLocalVarDef(Splc.LocalVarDefContext ctx) {
        // Project 3: define the variable before visiting its initializer.
        // Project 4: check initializer compatibility; array initialization is not supported.
        Token nameToken = ctx.variableDecl().Identifier().getSymbol();
        Type declaredType = resolveDeclaredType(ctx.variableDecl());

        if (declaredType != null) {
            if (currentScope.resolveLocal(nameToken.getText()) != null) {
                reporter.redefinedSymbol(nameToken);
            } else {
                currentScope.define(new VariableSymbol(nameToken.getText(), declaredType));
            }
        }

        if(ctx.expression()!=null){
            Type right = visitExpression(ctx.expression());
            if(!isSameType(declaredType, right)){
                Token assignToken = ctx.ASSIGN().getSymbol();
                reporter.typeMismatch(assignToken);
            }
            if (declaredType instanceof ArrayType) {
                reporter.arrayInitializerNotSupported(nameToken);
            }
        }
        return null;
    }

    private Type getFunctionReturnType() {
        for (Scope scope = currentScope; scope != null; scope = scope.getParent()) {
            if (scope instanceof FunctionSymbol function) {
                return function.getType();
            }
        }
        return null;
    }

    @Override
    public Type visitReturnStatement(Splc.ReturnStatementContext ctx) {
        // TODO: check the expression against the current function return type.
        Type actualType = visitExpression(ctx.expression());
        Type expectedType = getFunctionReturnType();
        if(!isSameType(actualType, expectedType)){
            Token rToken = ctx.RETURN().getSymbol();
            reporter.typeMismatch(rToken);
        }
        return null;
    }

    @Override
    public Type visitIfStatement(Splc.IfStatementContext ctx) {
        Type conditionType = visitExpression(ctx.expression());
        if (conditionType != null && !isInt(conditionType)) {
            reporter.badConditionType(ctx.IF().getSymbol());
        }
        for (Splc.BlockContext block : ctx.block()) {
            visit(block);
        }
        return null;
    }

    @Override
    public Type visitWhileStatement(Splc.WhileStatementContext ctx) {
        Type conditionType = visitExpression(ctx.expression());
        if (conditionType != null && !isInt(conditionType)) {
            reporter.badConditionType(ctx.WHILE().getSymbol());
        }
        visit(ctx.block());
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
                reporter.expectedStruct(ctx.DOT().getSymbol());
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

        // ===== 函数调用 =====
        if (ctx.Identifier() != null && ctx.LPAREN() != null) {
            Token functionToken = ctx.Identifier().getSymbol();
            Symbol symbol = currentScope.resolve(functionToken.getText());
            List<Splc.ExpressionContext> arguments = ctx.argumentList() == null
                    ? List.of()
                    : ctx.argumentList().expression();
            List<Type> argumentTypes = new ArrayList<>(arguments.size());
            for (Splc.ExpressionContext argument : arguments) {
                argumentTypes.add(visitExpression(argument));
            }

            if (symbol == null) {
                reporter.undeclaredIdentifier(functionToken);
                return null;
            }
            if (!(symbol instanceof FunctionSymbol function)) {
                reporter.notFunction(functionToken);
                return null;
            }

            List<VariableSymbol> parameters = function.getParameters();
            if (arguments.size() != parameters.size()) {
                reporter.badArgumentCount(functionToken);
            } else {
                for (int i = 0; i < arguments.size(); i++) {
                    Type actualType = argumentTypes.get(i);
                    if (actualType != null
                            && !isSameType(actualType, parameters.get(i).getType())) {
                        reporter.badArgumentType(arguments.get(i).getStart());
                    }
                }
            }
            return function.getType();
        }

        // ===== B：赋值 expression ASSIGN expression =====
        // 结果类型等于左侧左值类型，支持 a = b = c 的右结合链
        if (ctx.ASSIGN() != null) {
            Token assignToken = ctx.ASSIGN().getSymbol();
            Splc.ExpressionContext lhs = ctx.expression(0);
            Type lhsType = visitExpression(lhs);
            Type rhsType = visitExpression(ctx.expression(1));

            if (!isAssignable(lhs) || lhsType instanceof ArrayType) {
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
                // A：修正——未解析时必须报告 undeclaredIdentifier
                reporter.undeclaredIdentifier(ctx.Identifier().getSymbol());
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
        if (isParenthesized(expr)) {
            return isAssignable(expr.expression(0));
        }
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
    // A：增加 Identifier()==null 条件，避免把 f(x) 误判为括号表达式
    private boolean isParenthesized(Splc.ExpressionContext ctx) {
        return ctx.LPAREN() != null && ctx.RPAREN() != null && ctx.Identifier() == null;
    }

    // 辅助：是否一元运算符
    // A：改用表达式数量判断，避免把 a - b 误判为一元；补一元 +
    private boolean isUnaryOpCtx(Splc.ExpressionContext ctx) {
        return ctx.expression().size() == 1
                && (ctx.MINUS() != null || ctx.PLUS() != null || ctx.NOT() != null);
    }

    // 辅助：取一元运算符 Token
    private Token unaryOpToken(Splc.ExpressionContext ctx) {
        if (ctx.MINUS() != null) return ctx.MINUS().getSymbol();
        if (ctx.PLUS() != null) return ctx.PLUS().getSymbol();
        return ctx.NOT().getSymbol();
    }

    // 辅助：判断是否二元运算符（把长 if 抽出来，降低相似度）
    // A：补 MOD 与 POW
    private boolean isBinaryOpCtx(Splc.ExpressionContext ctx) {
        return ctx.PLUS() != null || ctx.MINUS() != null || ctx.MUL() != null
                || ctx.DIV() != null || ctx.MOD() != null || ctx.POW() != null
                || ctx.EQ() != null || ctx.NEQ() != null
                || ctx.LT() != null || ctx.GT() != null || ctx.LE() != null
                || ctx.GE() != null || ctx.AND() != null || ctx.OR() != null;
    }

    // 辅助：取二元运算符 Token
    private Token binaryOpToken(Splc.ExpressionContext ctx) {
        return (Token) ctx.getChild(1).getPayload();
    }
}