package plc.project.generator;

import plc.project.analyzer.Ir;
import plc.project.analyzer.Type;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;



public final class Generator implements Ir.Visitor<StringBuilder, RuntimeException> {

    private final StringBuilder builder = new StringBuilder();
    private int indent = 0;

    private void newline(int indent) {
        builder.append("\n");
        builder.append("    ".repeat(indent));
    }

    @Override
    public StringBuilder visit(Ir.Source ir) {
        builder.append(Environment.imports()).append("\n\n");
        builder.append("public final class Main {").append("\n\n");
        builder.append(Environment.definitions()).append("\n");
        //Java doesn't allow for nested functions, but we will pretend it does.
        //To support simple programs involving functions, we will "hoist" any
        //variable/function declaration at the start of the program to allow
        //these functions to be used as valid Java.
        indent = 1;
        boolean main = false;
        for (var statement : ir.statements()) {
            newline(indent);
            if (!main) {
                if (statement instanceof Ir.Stmt.Let || statement instanceof Ir.Stmt.Def) {
                    builder.append("static ");
                } else {
                    builder.append("public static void main(String[] args) {");
                    main = true;
                    indent = 2;
                    newline(indent);
                }
            }
            visit(statement);
        }
        if (main) {
            builder.append("\n").append("    }");
        }
        indent = 0;
        builder.append("\n\n").append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Let ir) {
        builder.append(ir.type().jvmName());
        builder.append(" ");
        builder.append(ir.name());
        if (ir.value().isPresent()) {
            builder.append(" = ");
            visit(ir.value().get());
        }

        builder.append(";");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Def ir) {

        String rname = ir.returns().jvmName();
        builder.append(rname);
        builder.append(" ");
        builder.append(ir.name());
        builder.append("(");

        for (int i = 0; i < ir.parameters().size(); i++) {
            var prm = ir.parameters().get(i);

            builder.append(prm.type().jvmName());
            builder.append(" ");

            builder.append(prm.name());

            if (i != ir.parameters().size() - 1) {
                builder.append(", ");
            }
        }

        builder.append(") {");

        // body

        indent++;
        for (var s : ir.body()) {
            newline(indent);
            visit(s);
        }
        indent--;

        newline(indent);

        builder.append("}");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.If ir) {
        builder.append("if (");
        visit(ir.condition());
        builder.append(") {");

        indent++;


        for (var stmt : ir.thenBody()) {
            newline(indent);
            visit(stmt);
        }
        indent--;

        if (!ir.elseBody().isEmpty()) {
            newline(indent);
            builder.append("} else {");
            indent++;
            for (var stmt : ir.elseBody()) {
                newline(indent);
                visit(stmt);
            }

            indent--;
        }

        newline(indent);

        builder.append("}");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.For ir) {
        builder.append("for (");
        builder.append(ir.type().jvmName());

        builder.append(" ");
        builder.append(ir.name());

        builder.append(" : ");
        visit(ir.expression());
        builder.append(") {");

        indent++;
        for (var stmt : ir.body()) {
            newline(indent);
            visit(stmt);
        }
        indent--;

        newline(indent);
        builder.append("}");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Return ir) {
        builder.append("return ");

        if (ir.value().isPresent()) {
            visit(ir.value().get());
        }

        else {
            builder.append("null");
        }

        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Expression ir) {
        visit(ir.expression());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Variable ir) {
        builder.append(ir.variable().name());

        builder.append(" = ");
        visit(ir.value());

        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Property ir) {
        visit(ir.property().receiver());

        builder.append(".");
        builder.append(ir.property().name());
        builder.append(" = ");

        visit(ir.value());
        builder.append(";");

        return builder;
    }
    @Override
    public StringBuilder visit(Ir.Expr.Literal ir) {
        var literal = switch (ir.value()) {
            case null -> "null";
            case Boolean b -> b.toString();
            case BigInteger i -> "new BigInteger(\"" + i + "\")";
            case BigDecimal d -> "new BigDecimal(\"" + d + "\")";
            case Character c -> "\'" + c + "\'"; //Limitation: escapes unsupported
            case String s -> "\"" + s + "\""; //Limitation: escapes unsupported
            default -> throw new AssertionError(ir.value());
        };
        return builder.append(literal);
    }

    @Override
    public StringBuilder visit(Ir.Expr.Group ir) {
        builder.append("(");
        visit(ir.expression());

        builder.append(")");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Binary ir) {

        String op = ir.operator();

        switch (op) {

            // ============================
            // STRING CONCAT (+)
            // ============================
            case "+" -> {
                if (ir.type() == Type.STRING) {
                    visit(ir.left());
                    builder.append(" + ");
                    visit(ir.right());
                } else {
                    // BigInteger / BigDecimal +
                    builder.append("(");
                    visit(ir.left());
                    builder.append(").add(");
                    visit(ir.right());
                    builder.append(")");
                }
            }

            // ============================
            // SUBTRACT
            // ============================
            case "-" -> {
                builder.append("(");
                visit(ir.left());
                builder.append(").subtract(");
                visit(ir.right());
                builder.append(")");
            }

            // ============================
            // MULTIPLY
            // ============================
            case "*" -> {
                builder.append("(");
                visit(ir.left());
                builder.append(").multiply(");
                visit(ir.right());
                builder.append(")");
            }

            // ============================
            // DIVIDE
            // ============================
            case "/" -> {
                builder.append("(");
                visit(ir.left());
                builder.append(").divide(");
                visit(ir.right());

                // Decimal requires rounding mode — Integer does NOT
                if (ir.type() == Type.DECIMAL) {
                    builder.append(", RoundingMode.HALF_EVEN");
                }

                builder.append(")");
            }

            // ============================
            // COMPARISON (< <= > >=)
            // ============================
            case "<", "<=", ">", ">=" -> {
                builder.append("(");
                visit(ir.left());
                builder.append(").compareTo(");
                visit(ir.right());
                builder.append(") ").append(op).append(" 0");
            }

            // ============================
            // EQUALITY
            // ============================
            case "==" -> {
                builder.append("Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");
            }
            case "!=" -> {
                builder.append("!Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");
            }

            // ============================
            // AND — must parenthesize left if OR
            // ============================
            case "AND" -> {
                // Our language gives AND and OR the SAME precedence,
                // so (left OR right) must be wrapped when used inside AND.
                if (ir.left() instanceof Ir.Expr.Binary bin && bin.operator().equals("OR")) {
                    builder.append("(");
                    visit(ir.left());
                    builder.append(")");
                } else {
                    visit(ir.left());
                }

                builder.append(" && ");
                visit(ir.right());
            }

            // ============================
            // OR — no special wrapping needed
            // ============================
            case "OR" -> {
                visit(ir.left());
                builder.append(" || ");
                visit(ir.right());
            }

            // ============================
            // Fallback (should never happen)
            // ============================
            default -> throw new RuntimeException("Unknown binary operator: " + op);
        }

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Variable ir) {
        builder.append(ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Property ir) {
        visit(ir.receiver());

        builder.append(".");
        builder.append(ir.name());

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Function ir) {
        builder.append(ir.name());
        builder.append("(");

        for (int i = 0; i < ir.arguments().size(); i++) {
            visit(ir.arguments().get(i));
            if (i < ir.arguments().size() - 1) {
                builder.append(", ");
            }
        }

        builder.append(")");

        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Method ir) {
        visit(ir.receiver());

        builder.append(".");
        builder.append(ir.name());
        builder.append("(");

        for (int i = 0; i < ir.arguments().size(); i++) {
            visit(ir.arguments().get(i));

            if (i < ir.arguments().size() - 1) {
                builder.append(", ");
            }
        }

        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.ObjectExpr ir) {
        builder.append("new Object() {");

        indent++;

        for (var fld : ir.fields()) {
            newline(indent);
            visit(fld);
        }
        for (var mthd : ir.methods()) {
            newline(indent);
            visit(mthd);
        }
        indent--;

        newline(indent);
        builder.append("}");
        return builder;
    }

}
