package app;

import javax.swing.JPanel;
import java.util.Optional;

import static swingtree.UI.*;

/**
 *  If all else fails, this view will tell the user what went wrong!
 *  And even if the user does not understand the error... at least they
 *  know that an error occurred! :)
 */
public class FatalErrorView extends JPanel
{
    public FatalErrorView(Exception e)
    {
        of(this).withLayout(FILL.and(WRAP(1)))
        .withPrefSize(1100, 500)
        .add(GROW,
            panel("alignx center, aligny center")
            .withPrefSize(825, 300)
            .add(WRAP,
                label("A fatal error prevents the application from launching.")
            )
            .add(GROW.and(SPAN),
                label("Please send this error message to the maintainer of Tribalism:")
            )
            .add(GROW.and(SPAN), separator())
            .add("alignx center, aligny center, wrap",
                scrollPane().add(
                    panel(FILL.and(INS(16))).add(label(nicelyHtmlFormattedError(e)))
                )
            )
            .add(GROW.and(SPAN), separator())
        )
        .add("span, alignx center, aligny top",
            button("Reset Save Folder").isEnabledIf(false)
            .onClick( it -> {
                // TODO
            })
        );
    }

    /**
     *  Turns the exception into a nicely formatted HTML string
     *  which includes the stack trace, error message, error type...
     *  Everything that is useful for debugging.
     *
     * @param e The exception to format
     * @return The formatted HTML string
     */
    private String nicelyHtmlFormattedError(Exception e) {
        try {
            return "<html><body>" + _recursivelyHtmlFormattedError(e) + "</body></html>";
        } catch (Exception reallyEmbarrasingException) {
            return nicelyHtmlFormattedError(reallyEmbarrasingException);
        }
    }

    private String _recursivelyHtmlFormattedError(Exception e) {
        StringBuilder html = new StringBuilder();
        String message = Optional.ofNullable(e.getMessage()).orElse("No message");
        html.append("<h1>").append(e.getClass().getName()).append("</h1>");
        html.append("<p>").append(message.replace("\n", "<br>")).append("</p>");

        html.append("<h2>Stack Trace</h2>");
        html.append("<pre>");
        for ( StackTraceElement element : e.getStackTrace() )
            html.append(element.toString()).append("<br>");
        html.append("</pre>");
        if ( e.getCause() instanceof Exception ) {
            html.append("<h2>Cause:</h2>");
            // We are using recursion here to print the cause of the cause of the cause...
            html.append(nicelyHtmlFormattedError((Exception) e.getCause()));
        }
        return html.toString();
    }
}
