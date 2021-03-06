/*
 * Copyright © Magento, Inc. All rights reserved.
 * See COPYING.txt for license details.
 */
package com.magento.idea.magento2plugin.inspections.php;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.PhpBundle;
import com.jetbrains.php.PhpClassHierarchyUtils;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.PhpLangUtil;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import com.magento.idea.magento2plugin.bundles.InspectionBundle;
import com.magento.idea.magento2plugin.inspections.php.util.PhpClassImplementsInterfaceUtil;
import com.magento.idea.magento2plugin.magento.files.Plugin;
import com.magento.idea.magento2plugin.magento.packages.Package;
import com.magento.idea.magento2plugin.util.GetPhpClassByFQN;
import com.magento.idea.magento2plugin.util.magento.plugin.GetTargetClassNamesByPluginClassName;
import org.jetbrains.annotations.NotNull;
import java.util.*;

public class PluginInspection extends PhpInspection {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder problemsHolder, boolean b) {
        return new PhpElementVisitor() {
            private final Integer beforePluginExtraParamsStart = 2;
            private final Integer afterAndAroundPluginExtraParamsStart = 3;
            private InspectionBundle inspectionBundle = new InspectionBundle();

            private String getPluginPrefix(Method pluginMethod) {
                String pluginMethodName = pluginMethod.getName();
                if (pluginMethodName.startsWith(Plugin.PluginType.around.toString())) {
                    return Plugin.PluginType.around.toString();
                }
                if (pluginMethodName.startsWith(Plugin.PluginType.before.toString())) {
                    return Plugin.PluginType.before.toString();
                }
                if (pluginMethodName.startsWith(Plugin.PluginType.after.toString())) {
                    return Plugin.PluginType.after.toString();
                }

                return null;
            }

            public void visitPhpMethod(Method pluginMethod) {
                String pluginPrefix = getPluginPrefix(pluginMethod);
                if (pluginPrefix == null) {
                    return;
                }

                PsiElement parentClass = pluginMethod.getParent();
                if (!(parentClass instanceof PhpClass)) {
                    return;
                }
                PsiElement currentClassNameIdentifier = ((PhpClass) parentClass).getNameIdentifier();
                String currentClass = ((PhpClass) parentClass).getFQN().substring(1);
                GetTargetClassNamesByPluginClassName targetClassesService = GetTargetClassNamesByPluginClassName.getInstance(problemsHolder.getProject());
                ArrayList<String> targetClassNames = targetClassesService.execute(currentClass);
                PhpIndex phpIndex = PhpIndex.getInstance(problemsHolder.getProject());

                for (String targetClassName : targetClassNames) {

                    PhpClass target = GetPhpClassByFQN.getInstance(problemsHolder.getProject()).execute(targetClassName);
                    if (target == null) {
                        return;
                    }
                    checkTargetClass(currentClassNameIdentifier, target);

                    String targetClassMethodName = getTargetMethodName(pluginMethod, pluginPrefix);
                    if (targetClassMethodName == null) {
                        return;
                    }
                    Method targetMethod = target.findMethodByName(targetClassMethodName);
                    if (targetMethod == null) {
                        return;
                    }
                    checkTargetMethod(pluginMethod, targetClassMethodName, targetMethod);
                    checkParametersCompatibility(pluginMethod, pluginPrefix, phpIndex, targetClassName, targetMethod);
                }
            }

            private void checkTargetClass(PsiElement currentClassNameIdentifier, PhpClass target) {
                if (target.isFinal()) {
                    ProblemDescriptor[] currentResults = problemsHolder.getResultsArray();
                    int finalClassProblems = getFinalClassProblems(currentResults);
                    if (finalClassProblems == 0) {
                        problemsHolder.registerProblem(
                            currentClassNameIdentifier,
                            inspectionBundle.message("inspection.plugin.error.finalClass"),
                            ProblemHighlightType.ERROR
                        );
                    }
                }
            }

            private void checkTargetMethod(Method pluginMethod, String targetClassMethodName, Method targetMethod) {
                if (targetClassMethodName.equals(Plugin.constructMethodName)) {
                    problemsHolder.registerProblem(
                        pluginMethod.getNameIdentifier(),
                        inspectionBundle.message("inspection.plugin.error.constructMethod"),
                        ProblemHighlightType.ERROR
                    );
                }
                if (targetMethod.isFinal()) {
                    problemsHolder.registerProblem(
                        pluginMethod.getNameIdentifier(),
                        inspectionBundle.message("inspection.plugin.error.finalMethod"),
                        ProblemHighlightType.ERROR
                    );
                }
                if (targetMethod.isStatic()) {
                    problemsHolder.registerProblem(
                        pluginMethod.getNameIdentifier(),
                        inspectionBundle.message("inspection.plugin.error.staticMethod"),
                        ProblemHighlightType.ERROR
                    );
                }
                if (!targetMethod.getAccess().toString().equals(Plugin.publicAccess)) {
                    problemsHolder.registerProblem(
                        pluginMethod.getNameIdentifier(),
                        inspectionBundle.message("inspection.plugin.error.nonPublicMethod"),
                        ProblemHighlightType.ERROR
                    );
                }
            }

            private void checkParametersCompatibility(Method pluginMethod, String pluginPrefix, PhpIndex phpIndex, String targetClassName, Method targetMethod) {
                Parameter[] targetMethodParameters = targetMethod.getParameters();
                Parameter[] pluginMethodParameters = pluginMethod.getParameters();

                int index = 0;
                for (Parameter pluginMethodParameter : pluginMethodParameters) {
                    index++;
                    String declaredType = pluginMethodParameter.getDeclaredType().toString();

                    if (index == 1) {
                        String targetClassFqn = Package.FQN_SEPARATOR.concat(targetClassName);
                        if (!checkTypeIncompatibility(targetClassFqn, declaredType, phpIndex)) {
                            problemsHolder.registerProblem(pluginMethodParameter, PhpBundle.message("inspection.wrong_param_type", new Object[]{declaredType, targetClassFqn}), ProblemHighlightType.ERROR);
                        }
                        if (!checkPossibleTypeIncompatibility(targetClassFqn, declaredType, phpIndex)) {
                            problemsHolder.registerProblem(
                                pluginMethodParameter,
                                inspectionBundle.message("inspection.plugin.error.typeIncompatibility"),
                                ProblemHighlightType.WEAK_WARNING
                            );
                        }
                        continue;
                    }
                    if (index == 2 && pluginPrefix.equals(Plugin.PluginType.around.toString())) {
                        if (!checkTypeIncompatibility(Plugin.CALLABLE_PARAM, declaredType, phpIndex) &&
                                !checkTypeIncompatibility(Package.FQN_SEPARATOR.concat(Plugin.CLOSURE_PARAM), declaredType, phpIndex)) {
                            problemsHolder.registerProblem(pluginMethodParameter, PhpBundle.message("inspection.wrong_param_type", new Object[]{declaredType, "callable"}), ProblemHighlightType.ERROR);
                        }
                        continue;
                    }
                    if (index == 2 && pluginPrefix.equals(Plugin.PluginType.after.toString()) &&
                            !targetMethod.getDeclaredType().toString().equals("void")) {
                        if (declaredType.isEmpty() || targetMethod.getDeclaredType().toString().isEmpty()) {
                            continue;
                        }
                        if (!checkTypeIncompatibility(targetMethod.getDeclaredType().toString(), declaredType, phpIndex)) {
                            problemsHolder.registerProblem(pluginMethodParameter, PhpBundle.message("inspection.wrong_param_type", new Object[]{declaredType, targetMethod.getDeclaredType().toString()}), ProblemHighlightType.ERROR);
                        }
                        if (!checkPossibleTypeIncompatibility(targetMethod.getDeclaredType().toString(), declaredType, phpIndex)) {
                            problemsHolder.registerProblem(
                                pluginMethodParameter,
                                inspectionBundle.message("inspection.plugin.error.typeIncompatibility"),
                                ProblemHighlightType.WEAK_WARNING
                            );
                        }
                        continue;
                    }
                    if (index == 2 && pluginPrefix.equals(Plugin.PluginType.after.toString()) &&
                            targetMethod.getDeclaredType().toString().equals("void")) {
                        if (declaredType.isEmpty()) {
                            continue;
                        }
                        if (!declaredType.equals("null")) {
                            problemsHolder.registerProblem(pluginMethodParameter, PhpBundle.message("inspection.wrong_param_type", new Object[]{declaredType, "null"}), ProblemHighlightType.ERROR);
                        }
                        continue;
                    }
                    int targetParameterKey = index - (pluginPrefix.equals(Plugin.PluginType.before.toString()) ?
                            beforePluginExtraParamsStart :
                            afterAndAroundPluginExtraParamsStart);
                    if (targetMethodParameters.length <= targetParameterKey) {
                        problemsHolder.registerProblem(
                            pluginMethodParameter,
                            inspectionBundle.message("inspection.plugin.error.redundantParameter"),
                            ProblemHighlightType.ERROR
                        );
                        continue;
                    }
                    Parameter targetMethodParameter = targetMethodParameters[targetParameterKey];
                    String targetMethodParameterDeclaredType = targetMethodParameter.getDeclaredType().toString();

                    if (!checkTypeIncompatibility(targetMethodParameterDeclaredType, declaredType, phpIndex)) {
                        problemsHolder.registerProblem(pluginMethodParameter, PhpBundle.message("inspection.wrong_param_type", new Object[]{declaredType, targetMethodParameterDeclaredType}), ProblemHighlightType.ERROR);
                    }
                    if (!checkPossibleTypeIncompatibility(targetMethodParameterDeclaredType, declaredType, phpIndex)) {
                        problemsHolder.registerProblem(
                            pluginMethodParameter,
                            inspectionBundle.message("inspection.plugin.error.typeIncompatibility"),
                            ProblemHighlightType.WEAK_WARNING
                        );
                    }
                }
            }



            private String getTargetMethodName(Method pluginMethod, String pluginPrefix) {
                String pluginMethodName = pluginMethod.getName();
                String targetClassMethodName = pluginMethodName.
                        replace(pluginPrefix, "");
                char firstCharOfTargetName = targetClassMethodName.charAt(0);
                int charType = Character.getType(firstCharOfTargetName);
                if (charType == Character.LOWERCASE_LETTER) {
                    return null;
                }
                return Character.toLowerCase(firstCharOfTargetName) + targetClassMethodName.substring(1);
            }

            private int getFinalClassProblems(ProblemDescriptor[] currentResults) {
                int finalClassProblems = 0;
                for (ProblemDescriptor currentProblem : currentResults) {
                    if (currentProblem.getDescriptionTemplate().equals(inspectionBundle.message("inspection.plugin.error.finalClass"))) {
                        finalClassProblems++;
                    }
                }
                return finalClassProblems;
            }


            private boolean checkTypeIncompatibility(String targetType, String declaredType, PhpIndex phpIndex) {
                if (targetType.isEmpty() || declaredType.isEmpty()) {
                    return true;
                }

                if (declaredType.equals(targetType)) {
                    return true;
                }

                boolean isDeclaredTypeClass = PhpLangUtil.isFqn(declaredType);
                boolean isTargetTypeClass = PhpLangUtil.isFqn(targetType);
                if (!isTargetTypeClass && isDeclaredTypeClass) {
                    return false;
                }

                GetPhpClassByFQN getPhpClassByFQN = GetPhpClassByFQN.getInstance(problemsHolder.getProject());
                PhpClass targetClass = getPhpClassByFQN.execute(targetType);
                PhpClass declaredClass = getPhpClassByFQN.execute(declaredType);
                if (targetClass == null || declaredClass == null) {
                    return false;
                }

                if (declaredClass.isInterface() && PhpClassImplementsInterfaceUtil.execute(declaredClass, targetClass)) {
                    return true;
                }

                if (PhpClassHierarchyUtils.classesEqual(targetClass, declaredClass)) {
                    return true;
                }

                if (PhpClassHierarchyUtils.isSuperClass(targetClass, declaredClass, false)) {
                    return true;
                }

                if (targetClass.isInterface() && PhpClassImplementsInterfaceUtil.execute(targetClass, declaredClass)) {
                    return true;
                }

                if (PhpClassHierarchyUtils.isSuperClass(declaredClass, targetClass, false)) {
                    return true;
                }

                return false;
            }

            private boolean checkPossibleTypeIncompatibility(String targetType, String declaredType, PhpIndex phpIndex) {
                if (targetType.isEmpty() || declaredType.isEmpty()) {
                    return true;
                }

                GetPhpClassByFQN getPhpClassByFQN = GetPhpClassByFQN.getInstance(problemsHolder.getProject());
                PhpClass targetClass = getPhpClassByFQN.execute(targetType);
                PhpClass declaredClass = getPhpClassByFQN.execute(declaredType);
                if (targetClass == null || declaredClass == null) {
                    return true;
                }
                if (PhpClassHierarchyUtils.isSuperClass(declaredClass, targetClass, false)) {
                    return false;
                }
                if (targetClass.isInterface() && PhpClassImplementsInterfaceUtil.execute(targetClass, declaredClass)) {
                    return false;
                }

                return true;
            }
        };
    }
}
