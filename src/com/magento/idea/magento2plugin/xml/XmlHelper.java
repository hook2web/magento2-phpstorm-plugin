package com.magento.idea.magento2plugin.xml;

import com.intellij.patterns.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;

/**
 * Created by Warider on 17.08.2015.
 */
public class XmlHelper {
    /**
     * <type name="\Namespace\Class">
     */
    public static XmlAttributeValuePattern getDiTypePattern() {
        return XmlPatterns
                .xmlAttributeValue()
                .withParent(
                    XmlPatterns
                        .xmlAttribute("name")
                        .withParent(
                            XmlPatterns
                                .xmlTag()
                                .withName("type")
                        )
                ).inside(
                    XmlHelper.getInsideTagPattern("type")
                ).inFile(XmlHelper.getXmlFilePattern());
    }

    /**
     * <preference type="\Namespace\Class">
     */
    public static XmlAttributeValuePattern getDiPreferencePattern() {
        return XmlPatterns
            .xmlAttributeValue()
            .withParent(
                XmlPatterns
                    .xmlAttribute("type")
                    .withParent(
                        XmlPatterns
                            .xmlTag()
                            .withName("preference")
                    )
            ).inside(
                XmlHelper.getInsideTagPattern("preference")
            ).inFile(XmlHelper.getXmlFilePattern());
    }

    /**
     * <argument name="argumentName" xsi:type="object">\Namespace\Class</argument>
     */
    public static PsiElementPattern.Capture<PsiElement> getArgumentObjectPattern() {
        return XmlPatterns
            .psiElement(XmlTokenType.XML_DATA_CHARACTERS)
            .withParent(
                XmlPatterns
                    .xmlText()
                    .withParent(XmlPatterns
                        .xmlTag()
                        .withName("argument")
                        .withAttributeValue("xsi:type", "object")
                    )
            ).inFile(XmlHelper.getXmlFilePattern());
    }

    /**
     * <item name="argumentName" xsi:type="object">\Namespace\Class</argument>
     */
    public static PsiElementPattern.Capture<PsiElement> getItemObjectPattern() {
        return XmlPatterns
            .psiElement(XmlTokenType.XML_DATA_CHARACTERS)
            .withParent(
                XmlPatterns
                    .xmlText()
                    .withParent(XmlPatterns
                            .xmlTag()
                            .withName("item")
                            .withAttributeValue("xsi:type", "object")
                    )
            ).inFile(XmlHelper.getXmlFilePattern());
    }

    /**
     * <virtualType type="\Namespace\Class">
     */
    public static XmlAttributeValuePattern getDiVirtualTypePattern() {
        return XmlPatterns
                .xmlAttributeValue()
                .withParent(
                        XmlPatterns
                                .xmlAttribute("type")
                                .withParent(
                                        XmlPatterns
                                                .xmlTag()
                                                .withName("virtualType")
                                )
                ).inside(
                        XmlHelper.getInsideTagPattern("virtualType")
                ).inFile(XmlHelper.getXmlFilePattern());
    }

    public static PsiFilePattern.Capture<PsiFile> getXmlFilePattern() {
        return XmlPatterns.psiFile()
                .withName(XmlPatterns
                        .string().endsWith(".xml")
                );
    }

    public static PsiElementPattern.Capture<XmlTag> getInsideTagPattern(String insideTagName) {
        return XmlPatterns.psiElement(XmlTag.class).withName(insideTagName);
    }

    public static PsiElementPattern.Capture<PsiElement> getTagPattern(String... tags) {
        return XmlPatterns
            .psiElement()
            .inside(XmlPatterns
                    .xmlAttributeValue()
                    .inside(XmlPatterns
                            .xmlAttribute()
                            .withName(StandardPatterns.string().oneOfIgnoreCase(tags)
                            )
                    )
            );
    }
}