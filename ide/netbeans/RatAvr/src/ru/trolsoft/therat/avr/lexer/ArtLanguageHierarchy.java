/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.trolsoft.therat.avr.lexer;

import java.util.Collection;
import java.util.EnumSet;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;

/**
 *
 * @author trol
 */
public class ArtLanguageHierarchy extends LanguageHierarchy<ArtTokenId> {

    @Override
    protected synchronized Collection<ArtTokenId> createTokenIds() {
        return EnumSet.allOf(ArtTokenId.class);
    }

    @Override
    protected synchronized Lexer<ArtTokenId> createLexer(LexerRestartInfo<ArtTokenId> info) {
        return new ArtLexer(info);
    }

    @Override
    protected String mimeType() {
        return "text/x-art";
    }
}
