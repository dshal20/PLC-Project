package plc.project.analyzer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Environment {

    public static final Map<String, Type> TYPES = Stream.of(
        Type.ANY,
        Type.NIL,
        Type.DYNAMIC,

        Type.BOOLEAN,
        Type.INTEGER,
        Type.DECIMAL,
        Type.CHARACTER,
        Type.STRING,

        Type.EQUATABLE,
        Type.COMPARABLE,
        Type.ITERABLE
    ).collect(Collectors.toMap(Type.Primitive::name, t -> t));

    public static Scope scope() {
        var scope = new Scope(null);
        //Helper variables for testing non-literal types;
        scope.define("any", Type.ANY);
        scope.define("dynamic", Type.DYNAMIC);
        scope.define("equatable", Type.EQUATABLE);
        scope.define("comparable", Type.COMPARABLE);
        scope.define("iterable", Type.ITERABLE);
        //"Native" functions for printing and creating lists.
        // log(argument) { return argument } | (Any) -> Dynamic
        scope.define("log", new Type.Function(List.of(Type.ANY), Type.DYNAMIC)); //note Dynamic!
        //"list" has been removed, since our type system can't represent is (why?)
        scope.define("debug", new Type.Function(List.of(Type.ANY), Type.NIL));
        scope.define("print", new Type.Function(List.of(Type.ANY), Type.NIL));
        scope.define("range", new Type.Function(List.of(Type.INTEGER, Type.INTEGER), Type.ITERABLE));
        //Helpers for testing variables, functions, and objects.
        scope.define("variable", Type.STRING);
        scope.define("function", new Type.Function(List.of(), Type.NIL));
        scope.define("functionAny", new Type.Function(List.of(Type.ANY), Type.ANY));
        scope.define("functionString", new Type.Function(List.of(Type.STRING), Type.STRING));
        var prototype = new Type.ObjectType(Optional.of("Prototype"), new Scope(null));
        prototype.scope().define("inherited_property", Type.STRING);
        prototype.scope().define("inherited_method", new Type.Function(List.of(), Type.NIL));
        var object = new Type.ObjectType(Optional.of("Object"), new Scope(null));
        scope.define("object", object);
        object.scope().define("prototype", prototype);
        object.scope().define("property", Type.STRING);
        object.scope().define("method", new Type.Function(List.of(), Type.NIL));
        object.scope().define("methodAny", new Type.Function(List.of(Type.ANY), Type.ANY));
        object.scope().define("methodString", new Type.Function(List.of(Type.STRING), Type.STRING));
        return scope;
    }

    public static boolean isSubtypeOf(Type subtype, Type supertype) {
        // All types are subtypes of Any
        if (supertype.equals(Type.ANY)) {
            return true;
        }
        // All types are subtypes and supertypes of Dynamic
        if (subtype.equals(Type.DYNAMIC) || supertype.equals(Type.DYNAMIC)) {
            return true;
        }
        // All types are subtypes of themselves
        if (subtype.equals(supertype)) {
            return true;
        }
        // Nil, Comparable, and Iterable are subtypes of Equatable
        if (supertype.equals(Type.EQUATABLE)) {
            if (subtype.equals(Type.NIL) || subtype.equals(Type.COMPARABLE) || subtype.equals(Type.ITERABLE)) {
                return true;
            }
            // Anything that is Comparable counts as Equatable too.
            if (subtype.isSubtypeOf(Type.COMPARABLE)) {
                return true;
            }
        }
        // Boolean, Integer, Decimal, Character, and String are subtypes of Comparable
        if (supertype.equals(Type.COMPARABLE)) {
            if (subtype.equals(Type.BOOLEAN) || subtype.equals(Type.INTEGER) || subtype.equals(Type.DECIMAL) || subtype.equals(Type.CHARACTER) || subtype.equals(Type.STRING)) {
                return true;
            }
        }

        // bonus
        if (subtype instanceof Type.ObjectType && supertype instanceof Type.ObjectType) {
            Type curr = subtype;
            while (curr instanceof Type.ObjectType) {
                Type.ObjectType objectType = (Type.ObjectType) curr;

                if (objectType.equals(supertype)) {
                    return true;
                }

                var m = objectType.scope().collect(true);
                Type prototypeType = m.get("prototype");
                if (prototypeType == null) {
                    break;
                }

                if (prototypeType.equals(Type.DYNAMIC)) {
                    return true;
                }

                curr = prototypeType;
            }
        }
        return false;

    }


}
