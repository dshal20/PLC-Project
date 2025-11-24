package plc.project.analyzer;

import java.util.List;
import java.util.Optional;

/**
 * IMPORTANT: This is an API file and should not be modified by your submission.
 */
public sealed interface Ir {

    record Source(
        List<Stmt> statements
    ) implements Ir {}

    sealed interface Stmt extends Ir {

        record Let(
            String name,
            Type type,
            Optional<Expr> value
        ) implements Stmt {}

        record Def(
            String name,
            List<Parameter> parameters,
            Type returns,
            List<Stmt> body
        ) implements Stmt {
            public record Parameter(String name, Type type) {}
        }

        record If(
            Expr condition,
            List<Stmt> thenBody,
            List<Stmt> elseBody
        ) implements Stmt {}

        record For(
            String name,
            Type type,
            Expr expression,
            List<Stmt> body
        ) implements Stmt {}

        record Return(
            Optional<Expr> value
        ) implements Stmt {}

        record Expression(
            Expr expression
        ) implements Stmt {}

        sealed interface Assignment extends Stmt {

            record Variable(
                Expr.Variable variable,
                Expr value
            ) implements Assignment {}

            record Property(
                Expr.Property property,
                Expr value
            ) implements Assignment {}

        }

    }

    sealed interface Expr extends Ir {

        Type type();

        record Literal(
            Object value,
            Type type
        ) implements Expr {}

        record Group(
            Expr expression
        ) implements Expr {

            @Override
            public Type type() {
                return expression.type();
            }

        }

        record Binary(
            String operator,
            Expr left,
            Expr right,
            Type type
        ) implements Expr {}

        record Variable(
            String name,
            Type type
        ) implements Expr {}

        record Property(
            Expr receiver,
            String name,
            Type type
        ) implements Expr {}

        record Function(
            String name,
            List<Expr> arguments,
            Type type
        ) implements Expr {}

        record Method(
            Expr receiver,
            String name,
            List<Expr> arguments,
            Type type
        ) implements Expr {}

        //Using "ObjectExpr" to avoid confusion with Java's "Object"
        record ObjectExpr(
            Optional<String> name,
            List<Stmt.Let> fields,
            List<Stmt.Def> methods,
            Type type
        ) implements Expr {}

    }

}
