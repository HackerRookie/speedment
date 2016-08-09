package com.speedment.tool.internal.rule;

import com.speedment.common.injector.annotation.Inject;
import com.speedment.runtime.component.ProjectComponent;
import com.speedment.runtime.config.Document;
import com.speedment.runtime.config.Project;
import com.speedment.runtime.config.trait.HasAlias;
import com.speedment.runtime.internal.util.document.DocumentDbUtil;
import com.speedment.tool.component.IssueComponent;
import com.speedment.tool.rule.Issue;
import com.speedment.tool.rule.Rule;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A rule which checks the future Java names of generated entities.
 * <p>
 * If a user would want an entity with the same name as a class used internally by the
 * the generated code, this might cause conflicts during runtime. Imagine for example
 * that the user would call his entity 'Integer'. Thus, we list all types
 * that normally show up during code generation. 
 * <P>
 * This rule is not perfect. The TypeMapper types are not included, and might cause
 * problems. For example, if someone names their entity Time, and that entity's SQL
 * type is Time, this might cause conflicts.
 *
 * @author Simon Jonasson
 * @since 3.0.0
 */
public class ProtectedNameRule implements Rule{
    private final static String[] PROTECTED_NAMES = {
        "AbstractEntity", "AbstractApplicationMetadata", "AbstractSqlManager",
        "Boolean",
        "Char", "Class", "ComparableField", "ComparableFieldImpl",
        "Double",
        "Field", "FieldIdentifier", "Float",
        "Integer", "Identifier", "Injector", 
        "Long",
        "Optional", "Object", "OptionalUtil",
        "ProjectComponent",
        "ResultSet",
        "String", "StringField", "StringFieldImpl", "SqlManager", "Stream", "StringBuilder", "SQLException", "SpeedmentException", "StringJoiner", "Speedment",
        "Table", "TypeMapper", "Tuple", "Tuples"           
    };
    
    private final Pattern pattern;
    
    private @Inject ProjectComponent projectComponent;
    private @Inject IssueComponent issues;
    
    public ProtectedNameRule(){
        String regex = Arrays.stream(PROTECTED_NAMES).collect(Collectors.joining("|"));
        this.pattern = Pattern.compile( regex , Pattern.CASE_INSENSITIVE );
    }

    @Override
    public CompletableFuture<Boolean> verify() {
        return CompletableFuture.supplyAsync( () -> checkRule() );
    }
    
    private boolean checkRule(){
        final AtomicBoolean noIssues = new AtomicBoolean(true);
        final Project project = projectComponent.getProject();  
        
        DocumentDbUtil.traverseOver(project)
            .forEach(doc -> check(doc, noIssues));
        
        return noIssues.get();
    }
    
    private void check(Document document, AtomicBoolean noIssues){
        final HasAlias alias = HasAlias.of(document);
        final String docName = alias.getJavaName();
        if( pattern.matcher( docName ).matches() ){
            noIssues.set(false);            
            issues.post( new Issue() {
                @Override
                public String getTitle() {
                    return "Protected Name: " + docName;
                }

                @Override
                public String getDescription() {
                    return "The Type name " + docName +" is used internally by Speedment."
                        + " If this name is assigned to a generated entiry, it might cause"
                        + " conflicts in the generated code.\n"
                        + "You may still proceed with code generation, but please be aware that"
                        + " the generated code might contain errors. To fix this issue, rename the"
                        + " entity in question.";
                }

                @Override
                public Issue.Level getLevel() {
                    return Level.WARNING;
                }
            } );
        }
    }
}