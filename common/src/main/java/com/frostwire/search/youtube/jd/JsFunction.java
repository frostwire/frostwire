/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.search.youtube.jd;

import static com.frostwire.search.youtube.jd.JavaFunctions.escape;
import static com.frostwire.search.youtube.jd.JavaFunctions.isalpha;
import static com.frostwire.search.youtube.jd.JavaFunctions.isdigit;
import static com.frostwire.search.youtube.jd.JavaFunctions.join;
import static com.frostwire.search.youtube.jd.JavaFunctions.json_loads;
import static com.frostwire.search.youtube.jd.JavaFunctions.len;
import static com.frostwire.search.youtube.jd.JavaFunctions.list;
import static com.frostwire.search.youtube.jd.JavaFunctions.mscpy;
import static com.frostwire.search.youtube.jd.JavaFunctions.reverse;
import static com.frostwire.search.youtube.jd.JavaFunctions.slice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class JsFunction<T> {

    private final JsContext ctx;
    private final LambdaN initial_function;

    private final static String WS = "[ \\t\\n\\x0B\\f\\r]"; //whitespaces, line feeds, aka \s.
    private final static String VAR = "[a-zA-Z$0-9_]+";
    private final static String CODE = "{(?<code>[^\\}]+)\\}";

    public JsFunction(String jscode, String funcname) {
        this.ctx = new JsContext(jscode);
        this.initial_function = extract_function(ctx, funcname);
    }

    @SuppressWarnings("unchecked")
    public T eval(Object[] args) {
        try {
            return (T) initial_function.eval(args);
        } finally {
            // at this point we know that jscode is no longer necessary
            ctx.free();
        }
    }

    public T eval(Object s) {
        return eval(new Object[] { s });
    }

    private static Object interpret_statement(final JsContext ctx, String stmt, final Map<String, Object> local_vars, final int allow_recursion) {
        if (allow_recursion < 0) {
            throw new JsError("Recursion limit reached");
        }

        if (stmt.startsWith("var ")) {
            stmt = stmt.substring("var ".length());
        }

        final Matcher ass_m = Pattern.compile("^(?<out>[a-z]+)(\\[(?<index>.+?)\\])?=(?<expr>.*)$").matcher(stmt);
        Lambda1 assign;
        String expr;
        if (ass_m.find()) {
            if (ass_m.group("index") != null) {

                final Object lvar = local_vars.get(ass_m.group("out"));
                final Object idx = interpret_expression(ctx, ass_m.group("index"), local_vars, allow_recursion);
                assert idx instanceof Integer;

                assign = new Lambda1() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public Object eval(Object val) {
                        ((List<Object>) lvar).set((Integer) idx, val);
                        return val;
                    }
                };
                expr = ass_m.group("expr");
            } else {

                final String var = ass_m.group("out");

                assign = new Lambda1() {
                    @Override
                    public Object eval(Object val) {
                        local_vars.put(var, val);
                        return val;
                    }
                };
                expr = ass_m.group("expr");
            }
        } else if (stmt.startsWith("return ")) {
            assign = new Lambda1() {
                @Override
                public Object eval(Object v) {
                    return v;
                }
            };
            expr = stmt.substring("return ".length());
        } else {
            // Try interpreting it as an expression
            expr = stmt;
            assign = new Lambda1() {
                @Override
                public Object eval(Object v) {
                    return v;
                }
            };
        }

        Object v = interpret_expression(ctx, expr, local_vars, allow_recursion);
        return assign.eval(v);
    }

    @SuppressWarnings("unchecked")
    private static Object interpret_expression(final JsContext ctx, String expr, Map<String, Object> local_vars, int allow_recursion) {
        if (isdigit(expr)) {
            return Integer.valueOf(expr);
        }

        if (isalpha(expr)) {
            return local_vars.get(expr);
        }

        //        try:
        //            return json.loads(expr)
        //        except ValueError:
        //            pass
        Object jsl = json_loads(expr);
        if (jsl != null) {
            return jsl;
        }

        Matcher m = Pattern.compile("^(?<var>"+VAR+")\\.(?<member>[^\\(]+)(\\((?<args>[^\\(\\)]*)\\))?$").matcher(expr);
        if (m.find()) {
            String variable = m.group("var");
            String member = m.group("member");
            String arg_str = m.group("args");

            Object obj = null;
            if (local_vars.containsKey(variable)) {
                obj = local_vars.get(variable);
            } else {
                if (!ctx.objects.containsKey(variable)) {
                    ctx.objects.put(variable, extract_object(ctx, variable));
                }
                obj = ctx.objects.get(variable);
            }

            if (arg_str == null) {
                // Member access
                if (member.equals("length")) {
                    return len(obj);
                }
                return ((JsObject) obj).functions.get(member).eval(new Object[] {});
            }

            if (!expr.endsWith(")")) {
                throw new JsError("Error parsing js code");
            }
            List<Object> argvals = null;
            if (arg_str.equals("")) {
                argvals = new ArrayList<Object>();
            } else {
                argvals = new ArrayList<Object>();
                for (String v : arg_str.split(",")) {
                    argvals.add(interpret_expression(ctx, v, local_vars, 20));
                }
            }

            if (member.equals("split")) {
                //assert argvals == ('',)
                return list(obj);
            }
            if (member.equals("join")) {
                //assert len(argvals) == 1
                return join((List<Object>) obj, argvals.get(0));
            }
            if (member.equals("reverse")) {
                //assert len(argvals) == 0
                reverse(obj);
                return obj;
            }
            if (member.equals("slice")) {
                //assert len(argvals) == 1
                return slice(obj, (Integer) argvals.get(0));
            }
            if (member.equals("splice")) {
                //assert isinstance(obj, list)
                int index = (Integer) argvals.get(0);
                int howMany = (Integer) argvals.get(1);
                List<Object> res = new ArrayList<Object>();
                List<Object> list = (List<Object>) obj;
                for (int i = index; i < Math.min(index + howMany, len(obj)); i++) {
                    res.add(list.remove(index));
                }
                return res.toArray();
            }

            return ((JsObject) obj).functions.get(member).eval(argvals.toArray());
        }

        m = Pattern.compile("^(?<in>[a-z]+)\\[(?<idx>.+)\\]$").matcher(expr);
        if (m.find()) {
            Object val = local_vars.get(m.group("in"));
            Object idx = interpret_expression(ctx, m.group("idx"), local_vars, allow_recursion - 1);
            return ((List<?>) val).get((Integer) idx);
        }

        m = Pattern.compile("^(?<a>.+?)(?<op>[%])(?<b>.+?)$").matcher(expr);
        if (m.find()) {
            Object a = interpret_expression(ctx, m.group("a"), local_vars, allow_recursion);
            Object b = interpret_expression(ctx, m.group("b"), local_vars, allow_recursion);
            return (Integer) a % (Integer) b;
        }

        m = Pattern.compile("^(?<func>[a-zA-Z]+)\\((?<args>[a-z0-9,]+)\\)$").matcher(expr);
        if (m.find()) {
            String fname = m.group("func");
            if (!ctx.functions.containsKey(fname) && ctx.jscode.length() > 0) {
                ctx.functions.put(fname, extract_function(ctx, fname));
            }
            List<Object> argvals = new ArrayList<Object>();
            for (String v : m.group("args").split(",")) {
                if (isdigit(v)) {
                    argvals.add(Integer.valueOf(v));
                } else {
                    argvals.add(local_vars.get(v));
                }
            }
            return ctx.functions.get(fname).eval(argvals.toArray());
        }
        throw new JsError(String.format("Unsupported JS expression %s", expr));
    }

    private static JsObject extract_object(final JsContext ctx, String objname) {
        JsObject obj = new JsObject();
        String obj_mRegex = String.format("(var"+ WS +"+)?%1$s"+ WS +"*="+ WS +"*\\{",
                escape(objname)) + WS +"*(?<fields>("+VAR + WS +"*:"+ WS +"*function\\(.*?\\)"+ WS +"*\\{.*?\\}(,"+ WS +")*)*)\\}"+ WS +"*;";
        final Matcher obj_m = Pattern.compile(obj_mRegex).matcher(ctx.jscode);
        obj_m.find();
        String fields = obj_m.group("fields");
        // Currently, it only supports function definitions
        final Matcher fields_m = Pattern.compile("(?<key>"+VAR+")"+ WS +"*:"+ WS +"*function\\((?<args>[a-z,]+)\\)\\"+CODE).matcher(fields);

        while (fields_m.find()) {
            final String[] argnames = mscpy(fields_m.group("args").split(","));

            LambdaN f = build_function(ctx, argnames, fields_m.group("code"));

            obj.functions.put(fields_m.group("key"), f);
        }

        return obj;
    }

    private static LambdaN extract_function(final JsContext ctx, String funcname) {
        String func_mRegex = String.format("(%1$s"+WS+"*="+WS+"*function|function"+WS+"+%1$s|[\\{;,]%1$s"+WS+"*="+WS+"*function|var"+WS+"+%1$s"+WS+"*="+WS+"*function)"+WS+"*",
                escape(funcname)) + "\\((?<args>[a-z,]+)\\)\\"+CODE;
        final Matcher func_m = Pattern.compile(func_mRegex).matcher(ctx.jscode);
        if (!func_m.find()) {
            throw new JsError("JsFunction.extract_function(): Could not find JS function " + funcname);
        }
        
        final String[] argnames = mscpy(func_m.group("args").split(","));

        return build_function(ctx, argnames, func_m.group("code"));
    }

    private static LambdaN build_function(final JsContext ctx, final String[] argnames, String code) {
        final String[] stmts = mscpy(code.split(";"));
        for (int i = 0; i < stmts.length; i++) {
            stmts[i] = stmts[i].replaceAll("[\n\r]", "");
        }

        return new LambdaN() {
            @Override
            public Object eval(Object[] args) {
                Map<String, Object> local_vars = new HashMap<String, Object>();
                for (int i = 0; i < argnames.length; i++) {
                    local_vars.put(argnames[i], args[i]);
                }
                Object res = null;
                for (String stmt : stmts) {
                    res = interpret_statement(ctx, stmt.trim(), local_vars, 100);
                }
                return res;
            }
        };
    }
}
