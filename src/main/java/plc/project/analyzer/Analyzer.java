package plc.project.analyzer;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;

import java.util.List;
public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;

    public Analyzer(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException {
        Optional<Ir.Expr> valIr;
        if (ast.value().isPresent()) {
            valIr = Optional.of(visit(ast.value().get()));
        } else {
            valIr = Optional.empty();
        }

        Type t;
        if (ast.type().isPresent()) {
            String tName = ast.type().get();
            Type r = Environment.TYPES.get(tName);
            if (r == null) {
                throw new AnalyzeException("Invalid Type " + tName, Optional.of(ast));
            }
            t = r;
        }

        else if (valIr.isPresent()) {
            t = valIr.get().type();
        }
        else {
            t = Type.DYNAMIC;
        }

        if (valIr.isPresent() && !valIr.get().type().isSubtypeOf(t)) {
            throw new AnalyzeException("Invalid Type", Optional.of(ast));
        }

        if (scope.resolve(ast.name(), true).isPresent()) {
            throw new AnalyzeException("Invalid Type " + ast.name(), Optional.of(ast));
        }
        scope.define(ast.name(), t);
        return new Ir.Stmt.Let(ast.name(), t, valIr);

    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        List<Type> pts = new ArrayList<>();
        List<Ir.Stmt.Def.Parameter> ips = new ArrayList<>();

        for (int i = 0; i < ast.parameters().size(); i++) {
            String pname = ast.parameters().get(i);
            Optional<String> typeNameOpt = ast.parameterTypes().get(i);

            Type paramType;
            if (typeNameOpt.isPresent()) {
                String typeName = typeNameOpt.get();
                Type t = Environment.TYPES.get(typeName);
                if (t == null) {
                    throw new AnalyzeException("Invalid Type " + typeName, Optional.of(ast));
                }
                paramType = t;
            } else {
                paramType = Type.DYNAMIC;
            }

            pts.add(paramType);
            ips.add(new Ir.Stmt.Def.Parameter(pname, paramType));
        }

        Type rt;
        if (ast.returnType().isPresent()) {
            String tname = ast.returnType().get();
            Type t = Environment.TYPES.get(tname);


            if (t == null) {
                throw new AnalyzeException("Invalid Type " + tname, Optional.of(ast));
            }
            rt = t;
        } else {
            rt = Type.DYNAMIC;
        }

        if (scope.resolve(ast.name(), true).isPresent()) {
            throw new AnalyzeException("Invalid func " + ast.name(), Optional.of(ast));
        }
        Type.Function ft = new Type.Function(pts, rt);

        scope.define(ast.name(), ft);

        Scope pscope = scope;
        scope = new Scope(pscope);

        scope.define("__return_type__", rt);

        for (int i = 0; i < ast.parameters().size(); i++) {
            String pname = ast.parameters().get(i);
            Type pt = pts.get(i);

            if (scope.resolve(pname, true).isPresent()) {
                scope = pscope;
                throw new AnalyzeException("Invalid parameter " + pname, Optional.of(ast));
            }

            scope.define(pname, pt);
        }

        List<Ir.Stmt> bod = new ArrayList<>();
        for (Ast.Stmt stmt : ast.body()) {
            bod.add(visit(stmt));
        }

        scope = pscope;

        return new Ir.Stmt.Def(ast.name(), ips, rt, bod);
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        var cond = visit(ast.condition());

        if (!cond.type().isSubtypeOf(Type.BOOLEAN)) {
            throw new AnalyzeException("Invalid boolean", Optional.of(ast));
        }

        var then = new ArrayList<Ir.Stmt>();
        var ps = scope;
        scope = new Scope(ps);
        for (var stmt : ast.thenBody()) {
            then.add(visit(stmt));
        }
        scope = ps;


        var es = new ArrayList<Ir.Stmt>();
        scope = new Scope(ps);

        for (var stmt : ast.elseBody()) {
            es.add(visit(stmt));
        }
        scope = ps;


        return new Ir.Stmt.If(cond, then, es);
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        Ir.Expr e = visit(ast.expression());

        if (!e.type().isSubtypeOf(Type.ITERABLE)) {
            throw new AnalyzeException("Invalid exp", Optional.of(ast));
        }

        Type et = Type.INTEGER;

        var pscope = scope;
        scope = new Scope(pscope);

        if (scope.resolve(ast.name(), true).isPresent()) {
            scope = pscope;
            throw new AnalyzeException("Invalid Variable " + ast.name(), Optional.of(ast));
        }
        scope.define(ast.name(), et);

        var bod = new ArrayList<Ir.Stmt>();


        for (var stmt : ast.body()) {
            bod.add(visit(stmt));
        }

        scope = pscope;

        return new Ir.Stmt.For(ast.name(), et, e, bod);
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        Optional<Type> i = scope.resolve("__return_type__", false);
        if (i.isEmpty()) {
            throw new AnalyzeException("Invalid return", Optional.of(ast));
        }

        Type exp = i.get();
        Optional<Ir.Expr> val = Optional.empty();

        if (ast.value().isPresent()) {
            Ir.Expr expr = visit(ast.value().get());

            if (!expr.type().isSubtypeOf(exp)) {
                throw new AnalyzeException("Invalid return", Optional.of(ast));
            }

            val = Optional.of(expr);
        }

        return new Ir.Stmt.Return(val);
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        // core
        Ir.Expr val = visit(ast.value());

        if (ast.expression() instanceof Ast.Expr.Variable varExpr) {
            var r = scope.resolve(varExpr.name(), false);

            if (r.isEmpty()) {
                throw new AnalyzeException("Invalid Variable" + varExpr.name(), Optional.of(ast));
            }
            Type t = r.get();

            if (!val.type().isSubtypeOf(t)) {
                throw new AnalyzeException("Invalid Variable", Optional.of(ast));
            }

            var ir = new Ir.Expr.Variable(varExpr.name(), t);

            return new Ir.Stmt.Assignment.Variable(ir, val);
        }
        if (ast.expression() instanceof Ast.Expr.Property propExpr) {
            Ir.Expr.Property propIr = visit(propExpr);
            Type ptype = propIr.type();

            if (!val.type().isSubtypeOf(ptype)) {
                throw new AnalyzeException("Invalid assignment", Optional.of(ast));
            }

            return new Ir.Stmt.Assignment.Property(propIr, val);
        }
        throw new AnalyzeException("Invalid assignment", Optional.of(ast));
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr
    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException {
        var type = switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case Character _ -> Type.CHARACTER;
            case String _ -> Type.STRING;
            default -> throw new AssertionError(ast.value().getClass());
        };
        return new Ir.Expr.Literal(ast.value(), type);
    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException {
        var e = visit(ast.expression());
        return new Ir.Expr.Group(e);
    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        Ir.Expr l = visit(ast.left());
        Ir.Expr r = visit(ast.right());

        Type lt = l.type();
        Type rt = r.type();

        String oper = ast.operator();


        if (oper.equals("+") || oper.equals("-") || oper.equals("*") || oper.equals("/")) {
            if (lt.equals(Type.DYNAMIC) && rt.equals(Type.DYNAMIC)) {
                return new Ir.Expr.Binary(oper, l, r, Type.DYNAMIC);
            }

            boolean ld = lt.equals(Type.DYNAMIC);
            boolean rd = rt.equals(Type.DYNAMIC);

            if (!ld && !rd) {
                if (oper.equals("+") && (lt.equals(Type.STRING) || rt.equals(Type.STRING))) {
                    return new Ir.Expr.Binary(oper, l, r, Type.STRING);

                }

                boolean ln = lt.equals(Type.INTEGER) || lt.equals(Type.DECIMAL);
                boolean rn = rt.equals(Type.INTEGER) || rt.equals(Type.DECIMAL);

                if (ln && rn) {
                    if (lt.equals(Type.INTEGER) && rt.equals(Type.INTEGER)) {
                        return new Ir.Expr.Binary(oper, l, r, Type.INTEGER);
                    } else if (lt.equals(Type.DECIMAL) && rt.equals(Type.DECIMAL)) {
                        return new Ir.Expr.Binary(oper, l, r, Type.DECIMAL);
                    } else {
                        throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));
                    }
                }
                throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));

            }
            Type non;
            if (ld) {
                non = rt;
            } else {
                non = lt;
            }

            if (oper.equals("+")) {
                if (non.equals(Type.STRING)) {
                    return new Ir.Expr.Binary(oper, l, r, Type.STRING);
                }
                if (non.equals(Type.INTEGER) || non.equals(Type.DECIMAL)) {
                    return new Ir.Expr.Binary(oper, l, r, non);
                }
                throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));
            }

            if (non.equals(Type.INTEGER) || non.equals(Type.DECIMAL)) {
                return new Ir.Expr.Binary(oper, l, r, non);
            }

            throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));

        }
        else if (oper.equals("<") || oper.equals("<=") || oper.equals(">") || oper.equals(">=")) {
            if (!lt.isSubtypeOf(Type.COMPARABLE) || !rt.isSubtypeOf(Type.COMPARABLE)) {
                throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));
            }
            if (!(lt.isSubtypeOf(rt) || rt.isSubtypeOf(lt))) {
                throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));
            }
            return new Ir.Expr.Binary(oper, l, r, Type.BOOLEAN);
        }

        else if (oper.equals("AND") || oper.equals("OR")) {
            if (!lt.isSubtypeOf(Type.BOOLEAN) || !rt.isSubtypeOf(Type.BOOLEAN)) {
                throw new AnalyzeException("Invalid Boolean", Optional.of(ast));
            }
            return new Ir.Expr.Binary(oper, l, r, Type.BOOLEAN);
        }

        else if (oper.equals("==") || oper.equals("!=")) {
            boolean compatible = lt.isSubtypeOf(rt) || rt.isSubtypeOf(lt);
            if (!compatible) {
                throw new AnalyzeException("Invalid Operator", Optional.of(ast));
            }
            return new Ir.Expr.Binary(oper, l, r, Type.BOOLEAN);
        }
        else {
            throw new AnalyzeException("Invalid Operator " + oper, Optional.of(ast));
        }
    }

    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException {
        var t = scope.resolve(ast.name(), false);

        if (t.isEmpty()) {
            throw new AnalyzeException("Invalid variable " + ast.name(), Optional.of(ast));
        }
        return new Ir.Expr.Variable(ast.name(), t.get());
    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException {
        Ir.Expr rv = visit(ast.receiver());
        Type rvt = rv.type();

        if (rvt.equals(Type.DYNAMIC)) {
            return new Ir.Expr.Property(rv, ast.name(), Type.DYNAMIC);
        }

        if (rvt instanceof Type.ObjectType) {
            Type current = rvt;

            while (current instanceof Type.ObjectType) {
                Type.ObjectType ot = (Type.ObjectType) current;

                java.util.Map<String, Type> memb = ot.scope().collect(true);

                Type ptype = memb.get(ast.name());
                if (ptype != null) {
                    return new Ir.Expr.Property(rv, ast.name(), ptype);
                }

                Type pt = memb.get("prototype");
                if (pt instanceof Type.ObjectType) {
                    current = pt;
                } else {
                    break;
                }
            }

            throw new AnalyzeException("Invalid property " + ast.name(), Optional.of(ast));
        }

        throw new AnalyzeException("Invalid property", Optional.of(ast));
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        Optional<Type> res = scope.resolve(ast.name(), false);


        if (res.isEmpty() || !(res.get() instanceof Type.Function)) {
            throw new AnalyzeException("Invalid func " + ast.name(), Optional.of(ast));
        }

        Type.Function ft = (Type.Function) res.get();

        List<Ir.Expr> argI = new ArrayList<>();
        for (Ast.Expr argA : ast.arguments()) {

            Ir.Expr arg = visit(argA);
            argI.add(arg);
        }

        List<Type> parameterTypes = ft.parameters();
        if (parameterTypes.size() != argI.size()) {
            throw new AnalyzeException("Invalid func " + ast.name() + ".", Optional.of(ast));
        }

        for (int i = 0; i < argI.size(); i++) {
            Type at = argI.get(i).type();
            Type pt = parameterTypes.get(i);

            if (!at.isSubtypeOf(pt)) {
                throw new AnalyzeException("Invalid func", Optional.of(ast));
            }
        }

        return new Ir.Expr.Function(ast.name(), argI, ft.returns());
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        // not core do later
        Ir.Expr rv = visit(ast.receiver());
        Type rvt = rv.type();

        List<Ir.Expr> argI = new ArrayList<>();
        for (Ast.Expr argA : ast.arguments()) {
            Ir.Expr arg = visit(argA);
            argI.add(arg);
        }
        if (rvt.equals(Type.DYNAMIC)) {
            return new Ir.Expr.Method(rv, ast.name(), argI, Type.DYNAMIC);
        }
        if (rvt instanceof Type.ObjectType) {
            Type curr = rvt;

            while (curr instanceof Type.ObjectType) {
                Type.ObjectType ot = (Type.ObjectType) curr;

                var memb = ot.scope().collect(true);

                Type mt = memb.get(ast.name());
                if (mt instanceof Type.Function) {
                    Type.Function ft = (Type.Function) mt;

                    if (ft.parameters().size() != argI.size()) {
                        throw new AnalyzeException("Invalid method " + ast.name(), Optional.of(ast));
                    }

                    for (int i = 0; i < argI.size(); i++) {
                        Type argType = argI.get(i).type();
                        Type paramType = ft.parameters().get(i);

                        if (!argType.isSubtypeOf(paramType)) {
                            throw new AnalyzeException("Invalid method " + ast.name(), Optional.of(ast));
                        }
                    }

                    // valid method
                    return new Ir.Expr.Method(rv, ast.name(), argI, ft.returns());
                }

                Type pt = memb.get("prototype");
                if (pt instanceof Type.ObjectType) {
                    curr = pt;
                } else {
                    break;
                }
            }

            throw new AnalyzeException("Invalid method", Optional.of(ast));
        }

        throw new AnalyzeException("Invalid method", Optional.of(ast));
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        Scope oscope = new Scope(null);
        Type.ObjectType otype = new Type.ObjectType(ast.name(), oscope);

        List<Ir.Stmt.Let> fld = new ArrayList<>();


        List<Ir.Stmt.Def> mthd = new ArrayList<>();

        // Fields will adapt the same analysis behavior as LET, with the type being defined to the ObjectType
        for (Ast.Stmt.Let fieldAst : ast.fields()) {

            Optional<Ir.Expr> val = Optional.empty();
            if (fieldAst.value().isPresent()) {
                Ir.Expr valexp = visit(fieldAst.value().get());
                val = Optional.of(valexp);
            }

            Type ft;
            if (fieldAst.type().isPresent()) {
                String tname = fieldAst.type().get();
                Type t = Environment.TYPES.get(tname);
                if (t == null) {
                    throw new AnalyzeException("Invalid Type", Optional.of(fieldAst));
                }
                ft = t;
            } else {
                if (val.isPresent()) {
                    ft = val.get().type();
                } else {
                    ft = Type.DYNAMIC;
                }
            }

            if (val.isPresent() && !val.get().type().isSubtypeOf(ft)) {
                throw new AnalyzeException("Invalid Type", Optional.of(fieldAst));
            }

            if (oscope.resolve(fieldAst.name(), true).isPresent()) {
                throw new AnalyzeException("Invalid field", Optional.of(fieldAst));
            }

            oscope.define(fieldAst.name(), ft);
            fld.add(new Ir.Stmt.Let(fieldAst.name(), ft, val));
        }

        // Methods will adapt the same analysis behavior as DEF, with the type being defined to the ObjectType
        for (Ast.Stmt.Def methodAst : ast.methods()) {
            List<Type> ptype = new ArrayList<>();
            List<Ir.Stmt.Def.Parameter> irpm = new ArrayList<>();

            for (int i = 0; i < methodAst.parameters().size(); i++) {
                String prmName = methodAst.parameters().get(i);
                Optional<String> prmtname = methodAst.parameterTypes().get(i);

                Type prmtype;
                if (prmtname.isPresent()) {
                    String tname = prmtname.get();
                    Type t = Environment.TYPES.get(tname);
                    if (t == null) {
                        throw new AnalyzeException("Invalid Type " + tname, Optional.of(methodAst));
                    }
                    prmtype = t;
                } else {
                    prmtype = Type.DYNAMIC;
                }

                ptype.add(prmtype);
                irpm.add(new Ir.Stmt.Def.Parameter(prmName, prmtype));
            }

            // Return type
            Type ret;
            if (methodAst.returnType().isPresent()) {
                String tname = methodAst.returnType().get();
                Type t = Environment.TYPES.get(tname);
                if (t == null) {
                    throw new AnalyzeException("Invalid Type", Optional.of(methodAst));
                }
                ret = t;
            } else {
                ret = Type.DYNAMIC;
            }
            if (oscope.resolve(methodAst.name(), true).isPresent()) {
                throw new AnalyzeException("Invalid method", Optional.of(methodAst));
            }
            oscope.define(methodAst.name(), new Type.Function(ptype, ret));
            Scope pscope = scope;
            scope = new Scope(pscope);

            scope.define("__return_type__", ret);
            scope.define("this", otype);

            for (int i = 0; i < methodAst.parameters().size(); i++) {
                String pname = methodAst.parameters().get(i);
                Type prmtype = ptype.get(i);

                if (scope.resolve(pname, true).isPresent()) {
                    scope = pscope;
                    throw new AnalyzeException("Invalid parameter", Optional.of(methodAst));
                }

                scope.define(pname, prmtype);
            }

            List<Ir.Stmt> body = new ArrayList<>();
            for (Ast.Stmt stmt : methodAst.body()) {
                body.add(visit(stmt));
            }

            scope = pscope;

            mthd.add(new Ir.Stmt.Def(methodAst.name(), irpm, ret, body));
        }

        return new Ir.Expr.ObjectExpr(ast.name(), fld, mthd, otype);
    }

}
