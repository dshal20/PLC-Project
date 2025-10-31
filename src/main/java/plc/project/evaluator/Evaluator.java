package plc.project.evaluator;

import plc.project.parser.Ast;

import java.util.Optional;
import java.math.BigInteger;
import java.math.BigDecimal;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        // done
        RuntimeValue value = new RuntimeValue.Primitive(null);

        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
            return value;

        }
        catch (EvaluateException error) {

            if (error.getAst().orElse(null) instanceof Ast.Stmt.Return) {
                throw new EvaluateException("Outside of function", Optional.of(ast));
            }
            throw error;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        // core
        RuntimeValue value;
        if (ast.value().isPresent()) {
            value = visit(ast.value().get());
        } else {
            value = new RuntimeValue.Primitive(null);
        }
        try {
            scope.define(ast.name(), value);
        } catch (IllegalStateException ex) {
            throw new EvaluateException("Variable already defined in current scope.", Optional.of(ast));
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        final Scope ogScope = this.scope;


        var func = new RuntimeValue.Function(ast.name(), args -> {

            if (args.size() != ast.parameters().size()) {
                throw new EvaluateException("Invalid arg", Optional.of(ast));
            }

            // save scope

            Scope savedScope = this.scope;

            // implement try catch block
            try {
                var ps = new Scope(ogScope);

                for (int i = 0; i < ast.parameters().size(); i++) {
                    ps.define(ast.parameters().get(i), args.get(i));
                }

                this.scope = new Scope(ps);

                try {
                    for (var i : ast.body()) {

                        visit(i);
                    }
                    return new RuntimeValue.Primitive(null);
                }

                catch (EvaluateException err) {
                    if (err.getAst().orElse(null) instanceof Ast.Stmt.Return) {

                        return this.scope.resolve("__return__", true)
                                .orElse(new RuntimeValue.Primitive(null));
                    }
                    throw err;
                }
            }
            finally {

                this.scope = savedScope;
            }
        });

        try {
            scope.define(ast.name(), func);
        }
        catch (IllegalStateException ignore) {
            throw new EvaluateException("Invalid function", Optional.of(ast));
        }
        return func;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        // core
        var conditionVal = visit(ast.condition());

        var maybeCond = requireType(conditionVal, Boolean.class);
        if (maybeCond.isEmpty()) {
            throw new EvaluateException("IF condition must be Boolean.", Optional.of(ast.condition()));
        }
        var cond = maybeCond.get();
        RuntimeValue last = new RuntimeValue.Primitive(null);
        Scope og = scope;

        try {
            scope = new Scope(scope);
            var b = cond ? ast.thenBody() : ast.elseBody();

            for (var j : b) {
                last = visit(j);
            }
            return last;
        } finally {
            scope = og;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        var isIterable = visit(ast.expression());

        var l = requireType(isIterable, java.util.List.class)
                .orElseThrow(() -> new EvaluateException("Invalid FOR", Optional.of(ast)));

        for (Object obj : l) {
            var i = (RuntimeValue) obj;

            Scope ogScope = scope;

            try {
                Scope scopeIter = new Scope(scope);
                scopeIter.define(ast.name(), i);

                scope = scopeIter;

                Scope bscope = scope;

                try {
                    scope = new Scope(scope);
                    for (var stmt : ast.body()) {
                        visit(stmt);
                    }
                }
                finally {
                    scope = bscope;
                }
            }
            finally {
                scope = ogScope;
            }
        }
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        RuntimeValue val;
        if (ast.value().isPresent()) {
            val = visit(ast.value().get());
        } else {
            val = new RuntimeValue.Primitive(null);
        }


        if (scope.resolve("__return__", true).isPresent()) {
            scope.assign("__return__", val);
        } else {
            scope.define("__return__", val);
        }
        throw new EvaluateException("RETURN", Optional.of(ast));
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
        // done
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        // do
        var lefths = ast.expression();
        RuntimeValue.ObjectValue targetO = null; // only used for property case
        String pname = null;

        if (lefths instanceof Ast.Expr.Variable v) {
            if (scope.resolve(v.name(), false).isEmpty()) {
                throw new EvaluateException("Undefined variable.", Optional.of(lefths));
            }
        }
        else if (lefths instanceof Ast.Expr.Property p) {
            // receiver must be an object
            var recvVal = visit(p.receiver());
            if (!(recvVal instanceof RuntimeValue.ObjectValue obj)) {
                throw new EvaluateException("Receiver must be an object.", Optional.of(p.receiver()));
            }
            targetO = obj;
            pname = p.name();
            boolean b = obj.scope().resolve(pname, true).isPresent();
            var curr = obj;

            while (!b) {
                var pval = curr.scope().resolve("prototype", true).orElse(null);
                if (pval instanceof RuntimeValue.ObjectValue pobj) {
                    b = pobj.scope().resolve(pname, true).isPresent();
                    curr = pobj;
                }
                else {
                    break;
                }
            }

            if (!b) {
                throw new EvaluateException("Invalid property", Optional.of(lefths));
            }
        }
        else {
            throw new EvaluateException("Invalid assignment", Optional.of(lefths));
        }
        var value = visit(ast.value());
        if (lefths instanceof Ast.Expr.Variable var) {
            try {
                scope.assign(var.name(), value);
            }
            catch (IllegalStateException ignore) {
                throw new EvaluateException("Invalid variable", Optional.of(lefths));
            }
        }
        else { // property case
            if (targetO.scope().resolve(pname, true).isPresent()) {
                targetO.scope().assign(pname, value);
            }
            else {
                targetO.scope().define(pname, value);
            }
        }

        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        // easy
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        String o = ast.operator();
        RuntimeValue l = visit(ast.left());

        switch (o) {
            case "+": {
                RuntimeValue r = visit(ast.right());

                var leftstring = requireType(l, String.class);
                var rightstring = requireType(r, String.class);

                if (leftstring.isPresent() || rightstring.isPresent()) {
                    return new RuntimeValue.Primitive(l.print() + r.print());

                }

                var leftint = requireType(l, BigInteger.class);
                var rightint = requireType(r, BigInteger.class);
                if (leftint.isPresent() && rightint.isPresent()) {
                    return new RuntimeValue.Primitive(leftint.get().add(rightint.get()));
                }

                var leftdecimal = requireType(l, BigDecimal.class);
                var rightdecimal = requireType(r, BigDecimal.class);
                if (leftdecimal.isPresent() && rightdecimal.isPresent()) {
                    return new RuntimeValue.Primitive(leftdecimal.get().add(rightdecimal.get()));
                }

                throw new EvaluateException("Invalid '+'", Optional.of(ast.left()));
            }
            case "*", "-": {
                var leftint = requireType(l, BigInteger.class);
                var leftdecimal = requireType(l, BigDecimal.class);

                if (leftint.isEmpty() && leftdecimal.isEmpty()) {
                    throw new EvaluateException("Invalid left", Optional.of(ast.left()));
                }

                RuntimeValue r = visit(ast.right());


                if (leftint.isPresent()) {
                    var rightint  = requireType(r, BigInteger.class)
                        .orElseThrow(() -> new EvaluateException("Invalid right", Optional.of(ast.right())));
                    if (o.equals("-")) {
                        return new RuntimeValue.Primitive(leftint.get().subtract(rightint));
                    } else {
                        return new RuntimeValue.Primitive(leftint.get().multiply(rightint));
                    }


                }
                else {
                    var rightdecimal = requireType(r, BigDecimal.class)
                            .orElseThrow(() -> new EvaluateException("Invalid right", Optional.of(ast.right())));
                    if (o.equals("-")) {
                        return new RuntimeValue.Primitive(leftdecimal.get().subtract(rightdecimal));
                    } else {
                        return new RuntimeValue.Primitive(leftdecimal.get().multiply(rightdecimal));
                    }
                }

            }
            case "/": {
                var leftint = requireType(l, BigInteger.class);
                var leftdecimal = requireType(l, BigDecimal.class);

                if (leftint.isEmpty() && leftdecimal.isEmpty()) {
                    throw new EvaluateException("Invalid", Optional.of(ast.left()));
                }

                RuntimeValue r = visit(ast.right());

                if (leftint.isPresent()) {
                    var rightint = requireType(r, BigInteger.class)

                            .orElseThrow(() -> new EvaluateException("Invalid right", Optional.of(ast.right())));
                    if (rightint.equals(BigInteger.ZERO)) {
                        throw new EvaluateException("zero division error", Optional.of(ast));
                    }
                    return new RuntimeValue.Primitive(leftint.get().divide(rightint));
                } else {

                    var rightdecimal = requireType(r, BigDecimal.class)
                            .orElseThrow(() -> new EvaluateException("Invalid right", Optional.of(ast.right())));

                    if (rightdecimal.compareTo(BigDecimal.ZERO) == 0) {

                        throw new EvaluateException("zero division error", Optional.of(ast));
                    }

                    //
                    return new RuntimeValue.Primitive(leftdecimal.get().divide(rightdecimal, java.math.RoundingMode.HALF_EVEN));
                }
            }
            case "==", "!=": {
                RuntimeValue r = visit(ast.right());

                var lv = (l instanceof RuntimeValue.Primitive p) ? p.value() : l;
                var rv = (r instanceof RuntimeValue.Primitive p) ? p.value() : r;
                boolean eql = java.util.Objects.equals(lv, rv);

                return new RuntimeValue.Primitive(o.equals("==") ? eql : !eql);
            }
            case "<", "<=", ">", ">=": {
                RuntimeValue r = visit(ast.right());

                if (!(l instanceof RuntimeValue.Primitive lp)) {
                    throw new EvaluateException("Invalid left", Optional.of(ast.left()));
                }
                if (!(lp.value() instanceof Comparable lc)) {
                    throw new EvaluateException("Invalid left", Optional.of(ast.left()));
                }

                Object rw;
                if (r instanceof RuntimeValue.Primitive rp) {
                    rw = rp.value();
                } else {
                    rw = r;
                }

                if (rw == null) {
                    throw new EvaluateException("Invalid right", Optional.of(ast.right()));
                }
                if (!lc.getClass().isInstance(rw)) {
                    throw new EvaluateException("Invalid right", Optional.of(ast.right()));
                }

                @SuppressWarnings("unchecked")
                int comp = ((Comparable<Object>) lc).compareTo(rw);
                boolean b = switch (o) {
                    case ">"  -> comp > 0;
                    case "<"  -> comp < 0;
                    case "<=" -> comp <= 0;
                    default   -> comp >= 0;
                };
                return new RuntimeValue.Primitive(b);

            }
            case "AND": {
                var leftbool = requireType(l, Boolean.class)
                        .orElseThrow(() -> new EvaluateException("Invalid left", Optional.of(ast.left())));

                if (leftbool == false) {
                    return new RuntimeValue.Primitive(false);
                }

                var rightbool = requireType(visit(ast.right()), Boolean.class)
                        .orElseThrow(() -> new EvaluateException("Invalid right", Optional.of(ast.right())));
                return new RuntimeValue.Primitive(true && rightbool);
            }
            case "OR": {
                var leftbool = requireType(l, Boolean.class)
                        .orElseThrow(() -> new EvaluateException("Invalid left", Optional.of(ast.left())));

                if (leftbool == true) {
                    return new RuntimeValue.Primitive(true);
                }

                var rightbool = requireType(visit(ast.right()), Boolean.class)
                        .orElseThrow(() -> new EvaluateException("Invalid right", Optional.of(ast.right())));
                return new RuntimeValue.Primitive(false || rightbool);
            }
            default: throw new EvaluateException("Invalid operator: " + o, Optional.of(ast));

        }


    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        // easy/core/done
        return scope.resolve(ast.name(), false)
                .orElseThrow(() -> new EvaluateException("Undefined: " + ast.name(), Optional.of(ast)));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        var receiver = visit(ast.receiver());
        if (!(receiver instanceof RuntimeValue.ObjectValue objct)) {
            throw new EvaluateException("Invalid receiver", Optional.of(ast.receiver()));
        }

        // Look on the object
        RuntimeValue.ObjectValue curr = objct;
        while (true) {
            var here = curr.scope().resolve(ast.name(), true);
            if (here.isPresent()) {
                return here.get();
            }
            var pval = curr.scope().resolve("prototype", true);
            if (pval.isPresent() && pval.get() instanceof RuntimeValue.ObjectValue protoObj) {
                curr = protoObj; // move up the chain
            } else {
                break; // no more prototypes
            }
        }

        throw new EvaluateException("Invalid property", Optional.of(ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        // core
        var functionValue = scope.resolve(ast.name(), false)
                .orElseThrow(() -> new EvaluateException("Function '" + ast.name() + "' not defined.", Optional.of(ast)));

        var function = requireType(functionValue, RuntimeValue.Function.class)
                .orElseThrow(() -> new EvaluateException("'" + ast.name() + "' has error.", Optional.of(ast)));

        var evalArgs = new java.util.ArrayList<RuntimeValue>();

        for (var argExpr : ast.arguments()) {
            evalArgs.add(visit(argExpr));
        }

        // Execute function
        return function.definition().invoke(evalArgs);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        var receiver = visit(ast.receiver());
        if (!(receiver instanceof RuntimeValue.ObjectValue objct)) {
            throw new EvaluateException("Invalid receiver", Optional.of(ast.receiver()));
        }

        RuntimeValue mVal = null;
        RuntimeValue.ObjectValue o = objct;
        RuntimeValue.ObjectValue probe = objct;
        while (true) {
            var here = probe.scope().resolve(ast.name(), true);
            if (here.isPresent()) {
                mVal = here.get();
                o = probe;
                break;
            }
            var pval = probe.scope().resolve("prototype", true);
            if (pval.isPresent() && pval.get() instanceof RuntimeValue.ObjectValue p) {
                probe = p; // climb up
            } else {
                break;    // no more prototypes
            }
        }

        if (!(mVal instanceof RuntimeValue.Function func)) {
            throw new EvaluateException("Invalid method", Optional.of(ast));
        }

        boolean e = o.name().isPresent()
                && ("Object".equals(o.name().get()) || "Prototype".equals(o.name().get()));

        var args = new java.util.ArrayList<RuntimeValue>();
        if (e) {
            args.add(objct);
        }
        for (var arg : ast.arguments()) {
            args.add(visit(arg));
        }

        return func.definition().invoke(args);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        var o = new RuntimeValue.ObjectValue(ast.name(), new Scope(null));

        // save scope
        Scope ogscope = this.scope;

        try {
            this.scope = o.scope();

            // LET
            for (var i : ast.fields()) {
                visit(i);
            }
            // DEF
            for (var i : ast.methods()) {
                visit(i);
            }
            return o;
        }
        finally {
            this.scope = ogscope;
        }
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If type
     * is a subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value must be a {@link RuntimeValue.Primitive} and
     * the check applies to the primitive value.
     */
    private static <T> Optional<T> requireType(RuntimeValue value, Class<T> type) {
        //To be discussed in lecture
        Optional<Object> unwrapped = RuntimeValue.class.isAssignableFrom(type)
            ? Optional.of(value)
            : requireType(value, RuntimeValue.Primitive.class).map(RuntimeValue.Primitive::value);
        return (Optional<T>) unwrapped.filter(type::isInstance); //cast checked by isInstance
    }

}
