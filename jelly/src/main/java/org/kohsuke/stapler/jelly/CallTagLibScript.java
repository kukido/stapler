package org.kohsuke.stapler.jelly;

import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.JellyException;
import org.apache.commons.jelly.JellyTagException;
import org.apache.commons.jelly.Script;
import org.apache.commons.jelly.XMLOutput;
import org.apache.commons.jelly.expression.Expression;
import org.apache.commons.jelly.impl.DynamicTag;
import org.apache.commons.jelly.impl.ExpressionAttribute;
import org.apache.commons.jelly.impl.TagScript;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link TagScript} that invokes a {@link Script} as a tag.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CallTagLibScript extends TagScript {
    /**
     * Resolves to the definition of the script to call to.
     */
    protected abstract Script resolveDefinition(JellyContext context) throws JellyTagException;

    @Override
    public void run(final JellyContext context, XMLOutput output) throws JellyTagException {
        // evaluated values of the attributes
        Map args = new HashMap(attributes.size());

        for (Map.Entry<String, ExpressionAttribute> e : attributes.entrySet()) {
            Expression expression = e.getValue().exp;
            args.put(e.getKey(),expression.evaluate(context));
        }

        // create new context based on current attributes
        JellyContext newJellyContext = context.newJellyContext(args);
        newJellyContext.setVariable( "attrs", args );

        // <d:invokeBody> uses this to discover what to invoke
        newJellyContext.setVariable("org.apache.commons.jelly.body", new Script() {
            public Script compile() throws JellyException {
                return this;
            }

            /**
             * When &lt;d:invokeBody/> is used to call back into the calling script,
             * the Jelly name resolution rule is in such that the body is evaluated with
             * the variable scope of the &lt;d:invokeBody/> caller. This is very different
             * from a typical closure name resolution mechanism, where the body is evaluated
             * with the variable scope of where the body was created.
             *
             * <p>
             * More concretely, in Jelly, this often shows up as a problem as inability to
             * access the "attrs" variable from inside a body, because every {@link DynamicTag}
             * invocation sets this variable in a new scope.
             *
             * <p>
             * To counter this effect, this class temporarily restores the original "attrs"
             * when the body is evaluated. This makes the name resolution of 'attrs' work
             * like what programmers normally expect.
             *
             * <p>
             * The same problem also shows up as a lack of local variables &mdash; when a tag
             * calls into the body via &lt;d:invokeBody/>, the invoked body will see all the
             * variables that are defined in the caller, which is again not what a normal programming language
             * does. But unfortunately, changing this is too pervasive.
             */
            public void run(JellyContext nestedContext, XMLOutput output) throws JellyTagException {
                Map m = nestedContext.getVariables();
                Object oldAttrs = m.put("attrs",context.getVariable("attrs"));
                try {
                    getTagBody().run(nestedContext,output);
                } finally {
                    m.put("attrs",oldAttrs);
                }
            }
        });
        newJellyContext.setVariable("org.apache.commons.jelly.body.scope", context);
        final Script def = resolveDefinition(newJellyContext);

        if(JellyFacet.TRACE) {
            try {
                String source = getSource();
                String msg = "<" + source+">";
                output.comment(msg.toCharArray(),0,msg.length());
                def.run(newJellyContext, output);
                msg = "</" + source+">";
                output.comment(msg.toCharArray(),0,msg.length());
            } catch (SAXException e) {
                throw new JellyTagException(e);
            }
        } else {
            def.run(newJellyContext, output);
        }
    }

    protected String getSource() {
        return "{jelly:"+getNsUri()+"}:"+getLocalName();
    }
}
