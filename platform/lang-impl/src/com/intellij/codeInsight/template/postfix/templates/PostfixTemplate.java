// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateMetaData;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

public abstract class PostfixTemplate {
  @NotNull private final String myId;
  @NotNull private final String myPresentableName;
  @NotNull private final String myKey;
  @NotNull private final String myDescription;
  @NotNull private final String myExample;
  @Nullable private final PostfixTemplateProvider myProvider;

  /**
   * @deprecated use {@link #PostfixTemplate(String, String, String, PostfixTemplateProvider)}
   */
  protected PostfixTemplate(@NotNull String name, @NotNull String example) {
    this(null, name, "." + name, example, null);
  }

  protected PostfixTemplate(@Nullable String id,
                            @NotNull String name,
                            @NotNull String example,
                            @Nullable PostfixTemplateProvider provider) {
    this(id, name, "." + name, example, provider);
  }

  /**
   * @deprecated use {@link #PostfixTemplate(String, String, String, String, PostfixTemplateProvider)}
   */
  protected PostfixTemplate(@NotNull String name, @NotNull String key, @NotNull String example) {
    this(null, name, key, example, null);
  }

  protected PostfixTemplate(@Nullable String id,
                            @NotNull String name,
                            @NotNull String key,
                            @NotNull String example,
                            @Nullable PostfixTemplateProvider provider) {
    myId = id != null ? id : getClass().getName() + "#" + key;
    String tempDescription;
    myPresentableName = name;
    myKey = key;
    myExample = example;

    try {
      tempDescription = PostfixTemplateMetaData.createMetaData(this).getDescription().getText();
    }
    catch (IOException e) {
      tempDescription = "Under construction";
    }
    myDescription = tempDescription;
    myProvider = provider;
  }

  /**
   * Template's identifier. Used for saving the settings related to this templates.
   */
  @NotNull
  public String getId() {
    return myId;
  }

  /**
   * Template's key. Used while expanding template in editor.
   *
   * @return
   */
  @NotNull
  public final String getKey() {
    return myKey;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getExample() {
    return myExample;
  }

  public boolean startInWriteAction() {
    return true;
  }

  public boolean isEnabled(PostfixTemplateProvider provider) {
    final PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    return settings.isPostfixTemplatesEnabled() && settings.isTemplateEnabled(this, provider);
  }

  public abstract boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset);

  public abstract void expand(@NotNull PsiElement context, @NotNull Editor editor);

  @Nullable
  public PostfixTemplateProvider getProvider() {
    return myProvider;
  }

  /**
   * Builtin templates cannot be removed.
   * If they are editable, they can be restored to default.
   */
  public boolean isBuiltin() {
    return true;
  }

  /**
   * Template can be edit. Only templates whose key starts with . can be edited.
   */
  public boolean isEditable() {
    return getProvider() instanceof PostfixEditableTemplateProvider;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PostfixTemplate)) return false;
    PostfixTemplate template = (PostfixTemplate)o;
    return Objects.equals(myId, template.myId) &&
           Objects.equals(myPresentableName, template.myPresentableName) &&
           Objects.equals(myKey, template.myKey) &&
           Objects.equals(myDescription, template.myDescription) &&
           Objects.equals(myExample, template.myExample) &&
           Objects.equals(myProvider, template.myProvider);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myId, myPresentableName, myKey, myDescription, myExample, myProvider);
  }
}
