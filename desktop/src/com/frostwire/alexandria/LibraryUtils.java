/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.alexandria;

public final class LibraryUtils {

    private LibraryUtils() {
    }

    public static String luceneEncode(String s) {
        //+ - && || ! ( ) { } [ ] ^ " ~ * ? : \
        int length = s.length();
        s = s.replace("AND", " ").replace("OR", " ").replace("NOT", " ");
        StringBuilder buff = new StringBuilder(2 * length);
        for (int i = 0; i < length; i++) {
            try {
                char c = s.charAt(i);
                switch (c) {
                case '+':
                    buff.append("\\+");
                    break;
                case '-':
                    buff.append("\\-");
                    break;
                case '&':
                    buff.append("\\&");
                    break; // actually it's &&
                case '|':
                    buff.append("\\r");
                    break; // actually it's ||
                case '!':
                    buff.append("\\!");
                    break;
                case '(':
                    buff.append("\\(");
                    break;
                case ')':
                    buff.append("\\)");
                    break;
                case '{':
                    buff.append("\\{");
                    break;
                case '}':
                    buff.append("\\}");
                    break;
                case '[':
                    buff.append("\\[");
                    break;
                case ']':
                    buff.append("\\]");
                    break;
                case '^':
                    buff.append("\\^");
                    break;
                case '\"':
                    buff.append("\\\"");
                    break;
                case '~':
                    buff.append("\\~");
                    break;
                case '*':
                    buff.append("\\*");
                    break;
                case '?':
                    buff.append("\\?");
                    break;
                case ':':
                    buff.append("\\:");
                    break;
                case '\\':
                    buff.append("\\\\");
                    break;
                default:
                    buff.append(c);
                    break;
                }
            } catch (Throwable e) {
                break; // this could happens due to a bad encoding of characters in String.
            }
        }
        return buff.toString().replaceAll("\\s+", " ");
    }

    public static String fuzzyLuceneQuery(String str) {
        String luceneStr = luceneEncode(str);
        String[] tokens = luceneStr.split(" ");
        if (tokens.length == 0) {
            return luceneStr;
        }
        if (tokens.length == 1) {
            return luceneStr + "~";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append(tokens[i]).append("~ AND ");
        }
        sb.append(tokens[tokens.length - 1]).append("~");

        return sb.toString().trim();
    }
    
    public static String wildcardLuceneQuery(String str) {
        String luceneStr = luceneEncode(str);
        String[] tokens = luceneStr.split(" ");
        if (tokens.length == 0) {
            return luceneStr;
        }
        if (tokens.length == 1) {
            return luceneStr + "*";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append(tokens[i]).append("* AND ");
        }
        sb.append(tokens[tokens.length - 1]).append("*");

        return sb.toString().trim();
    }
}
