package com.qoomon.banking.swift.field;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by qoomon on 27/06/16.
 */
public class SwiftMTFieldParser {

    public static final String SEPARATOR_FIELD_TAG = "--";

    private static final Pattern FIELD_STRUCTURE_PATTERN = Pattern.compile(":(?<tag>[^:]+):(?<content>.*)");

    public List<GeneralMTField> parse(Reader mt940TextReader) {

        List<GeneralMTField> fieldList = new LinkedList<>();

        try (LineNumberReader lineReader = new LineNumberReader(mt940TextReader)) {
            GeneralMTFieldBuilder currentFieldBuilder = null;

            Set<MessageLineType> currentValidFieldSet = ImmutableSet.of(MessageLineType.FIELD);
            String currentMessageLine = lineReader.readLine();
            int currentMessageLineNumber = lineReader.getLineNumber();

            while (currentMessageLine != null) {
                String nextMessageLine;
                int nextMessageLineNumber;
                Set<MessageLineType> nextValidFieldSet;

                MessageLineType currentMessageLineType = determineMessageLineType(currentMessageLine);
                switch (currentMessageLineType) {
                    case FIELD: {
                        Matcher fieldMatcher = FIELD_STRUCTURE_PATTERN.matcher(currentMessageLine);
                        if (!fieldMatcher.matches()) {
                            throw new SwiftMTFieldParserException("Parse error: " + currentMessageLineType.name() + " did not match " + FIELD_STRUCTURE_PATTERN.pattern(), currentMessageLineNumber);
                        }

                        // start of a new field
                        currentFieldBuilder = new GeneralMTFieldBuilder()
                                .setTag(fieldMatcher.group("tag"))
                                .appendContent(fieldMatcher.group("content"));

                        nextValidFieldSet = ImmutableSet.of(MessageLineType.FIELD, MessageLineType.FIELD_CONTINUATION, MessageLineType.SEPARATOR);
                        break;
                    }
                    case FIELD_CONTINUATION: {
                        if (currentFieldBuilder == null) {
                            throw new SwiftMTFieldParserException("Bug: invalid order check for line type" + currentMessageLineType.name(), currentMessageLineNumber);
                        }
                        currentFieldBuilder.appendContent("\n")
                                .appendContent(currentMessageLine);
                        nextValidFieldSet = ImmutableSet.of(MessageLineType.FIELD, MessageLineType.FIELD_CONTINUATION, MessageLineType.SEPARATOR);
                        break;
                    }
                    case SEPARATOR: {
                        currentFieldBuilder = new GeneralMTFieldBuilder().setTag(SEPARATOR_FIELD_TAG);
                        nextValidFieldSet = ImmutableSet.of(MessageLineType.FIELD);
                        break;
                    }
                    default:
                        throw new SwiftMTFieldParserException("Bug: Missing handling for line type" + currentMessageLineType.name(), currentMessageLineNumber);

                }

                if (!currentValidFieldSet.contains(currentMessageLineType)) {
                    throw new SwiftMTFieldParserException("Parse error: unexpected line order of" + currentMessageLineType.name(), currentMessageLineNumber);
                }

                // prepare next line
                nextMessageLine = lineReader.readLine();
                nextMessageLineNumber = lineReader.getLineNumber();

                // handle finishing field
                if (nextMessageLine != null) {
                    MessageLineType nextMessageLineType = determineMessageLineType(nextMessageLine);
                    if (nextMessageLineType != MessageLineType.FIELD_CONTINUATION) {
                        fieldList.add(currentFieldBuilder.build());
                        currentFieldBuilder = null;
                    }
                } else { // end of reader
                    if (currentFieldBuilder != null) {
                        fieldList.add(currentFieldBuilder.build());
                        currentFieldBuilder = null;
                    }
                }

                // prepare for next iteration
                currentValidFieldSet = nextValidFieldSet;
                currentMessageLine = nextMessageLine;
                currentMessageLineNumber = nextMessageLineNumber;
            }

            return fieldList;
        } catch (IOException e) {
            throw new SwiftMTFieldParserException(e);
        }
    }

    private MessageLineType determineMessageLineType(String messageLine) {
        Preconditions.checkNotNull(messageLine);
        if (messageLine.isEmpty()) {
            return MessageLineType.EMPTY;
        }
        if (messageLine.equals(SEPARATOR_FIELD_TAG)) {
            return MessageLineType.SEPARATOR;
        }
        if (FIELD_STRUCTURE_PATTERN.matcher(messageLine).matches()) {
            return MessageLineType.FIELD;
        }
        return MessageLineType.FIELD_CONTINUATION;

    }

    private enum MessageLineType {
        FIELD,
        FIELD_CONTINUATION,
        SEPARATOR,
        EMPTY
    }


    private class GeneralMTFieldBuilder {

        String tag = null;

        StringBuilder contentBuilder = new StringBuilder();

        public GeneralMTField build() {
            return new GeneralMTField(tag, contentBuilder.toString());
        }

        public GeneralMTFieldBuilder setTag(String tag) {
            this.tag = tag;
            return this;
        }

        public GeneralMTFieldBuilder appendContent(String content) {
            this.contentBuilder.append(content);
            return this;
        }
    }

}
