package fr.axl.lvy.quote.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import fr.axl.lvy.base.ui.ViewToolbar;
import fr.axl.lvy.quote.Quote;
import fr.axl.lvy.quote.QuoteService;
import java.util.Optional;

@Route("devis")
@PageTitle("Devis")
@Menu(order = 2, icon = "vaadin:file-text-o", title = "Devis")
class QuoteListView extends VerticalLayout {

  private final QuoteService quoteService;
  private final Grid<Quote> grid;

  QuoteListView(QuoteService quoteService) {
    this.quoteService = quoteService;

    var addBtn = new Button("Nouveau devis", event -> Notification.show("TODO: formulaire devis"));
    addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

    grid = new Grid<>();
    grid.addColumn(Quote::getQuoteNumber).setHeader("N° Devis").setAutoWidth(true);
    grid.addColumn(quote -> Optional.ofNullable(quote.getClient()).map(c -> c.getName()).orElse(""))
        .setHeader("Client")
        .setFlexGrow(1);
    grid.addColumn(Quote::getQuoteDate).setHeader("Date").setAutoWidth(true);
    grid.addColumn(Quote::getValidityDate).setHeader("Validité").setAutoWidth(true);
    grid.addColumn(Quote::getTotalExclTax).setHeader("Total HT").setAutoWidth(true);
    grid.addColumn(Quote::getTotalInclTax).setHeader("Total TTC").setAutoWidth(true);
    grid.addColumn(quote -> quote.getStatus().name()).setHeader("Statut").setAutoWidth(true);
    grid.setEmptyStateText("Aucun devis");
    grid.setSizeFull();
    grid.addThemeVariants(GridVariant.LUMO_NO_BORDER);

    refreshGrid();

    setSizeFull();
    setPadding(false);
    setSpacing(false);
    getStyle().setOverflow(Style.Overflow.HIDDEN);

    add(new ViewToolbar("Devis", addBtn));
    add(grid);
  }

  private void refreshGrid() {
    grid.setItems(quoteService.findAll());
  }
}
