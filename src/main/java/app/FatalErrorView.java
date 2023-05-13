package app;

import javax.swing.*;

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
        StringBuilder html = new StringBuilder("<html><body>");
        html.append("<h1>").append(e.getClass().getName()).append("</h1>");
        html.append("<p>").append(e.getMessage().replace("\n", "<br>")).append("</p>");
        if ( e.getCause() != null ) {
            html.append("<h2>Cause</h2>");
            html.append("<p>").append(e.getCause().getMessage().replace("\n", "<br>")).append("</p>");
        }
        html.append("<h2>Stack Trace</h2>");
        html.append("<pre>");
        for ( StackTraceElement element : e.getStackTrace() )
            html.append(element.toString()).append("<br>");
        html.append("</pre>");
        html.append("</body></html>");
        return html.toString();
    }
}
