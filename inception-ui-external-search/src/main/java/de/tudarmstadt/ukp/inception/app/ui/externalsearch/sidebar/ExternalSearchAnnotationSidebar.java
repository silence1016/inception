/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.app.ui.externalsearch.sidebar;

import static de.tudarmstadt.ukp.inception.app.ui.externalsearch.sidebar.ExternalSearchUserStateMetaData.CURRENT_ES_USER_STATE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.text.Highlighter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wicketstuff.event.annotation.OnEvent;

import de.agilecoders.wicket.extensions.markup.html.bootstrap.form.select.BootstrapSelect;
import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.event.RenderAnnotationsEvent;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VMarker;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VTextMarker;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxFormComponentUpdatingBehavior;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaAjaxSubmitLink;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaModelAdapter;
import de.tudarmstadt.ukp.clarin.webanno.support.spring.ApplicationEventPublisherHolder;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebar_ImplBase;
import de.tudarmstadt.ukp.inception.app.ui.externalsearch.ExternalResultDataProvider;
import de.tudarmstadt.ukp.inception.app.ui.externalsearch.utils.DocumentImporter;
import de.tudarmstadt.ukp.inception.app.ui.externalsearch.utils.Utilities;
import de.tudarmstadt.ukp.inception.externalsearch.ExternalSearchHighlight;
import de.tudarmstadt.ukp.inception.externalsearch.ExternalSearchResult;
import de.tudarmstadt.ukp.inception.externalsearch.ExternalSearchService;
import de.tudarmstadt.ukp.inception.externalsearch.HighlightUtils;
import de.tudarmstadt.ukp.inception.externalsearch.event.ExternalSearchQueryEvent;
import de.tudarmstadt.ukp.inception.externalsearch.model.DocumentRepository;
import de.tudarmstadt.ukp.inception.support.annotation.OffsetSpan;
import eu.openminted.share.annotations.api.Component;

public class ExternalSearchAnnotationSidebar
    extends AnnotationSidebar_ImplBase
{
    private static final long serialVersionUID = -3358207848681467994L;

    private static final Logger LOG = LoggerFactory
        .getLogger(ExternalSearchAnnotationSidebar.class);

    private @SpringBean DocumentService documentService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean ProjectService projectService;
    private @SpringBean ExternalSearchService externalSearchService;
    private @SpringBean UserDao userRepository;
    private @SpringBean ImportExportService importExportService;
    private @SpringBean ApplicationEventPublisherHolder applicationEventPublisher;
    private @SpringBean DocumentImporter documentImporter;

    private CompoundPropertyModel<ExternalSearchUserState> searchStateModel;

    private final WebMarkupContainer mainContainer;

    private IModel<List<DocumentRepository>> repositoriesModel;

    private Project project;

    private DocumentRepository currentRepository;

    private WebMarkupContainer dataTableContainer;

    public ExternalSearchAnnotationSidebar(String aId, IModel<AnnotatorState> aModel,
        AnnotationActionHandler aActionHandler, CasProvider aCasProvider,
        AnnotationPage aAnnotationPage)
    {
        super(aId, aModel, aActionHandler, aCasProvider, aAnnotationPage);

        // Attach search state to annotation page
        // This state is to maintain persistence of this sidebar so that when user moves to another
        // sidebar and comes back here, the state of this sidebar (search results) are preserved.
        searchStateModel = new CompoundPropertyModel<>(LambdaModelAdapter.of(
            () -> aAnnotationPage.getMetaData(CURRENT_ES_USER_STATE),
            searchState -> aAnnotationPage.setMetaData(CURRENT_ES_USER_STATE, searchState)));

        // Set up the search state in the page if it is not already there
        if (aAnnotationPage.getMetaData(CURRENT_ES_USER_STATE) == null) {
            searchStateModel.setObject(new ExternalSearchUserState());
        }

        project = getModel().getObject().getProject();
        List<DocumentRepository> repositories = externalSearchService
            .listDocumentRepositories(project);

        ExternalSearchUserState searchState = searchStateModel.getObject();
        currentRepository = searchState.getCurrentRepository();
        if (currentRepository == null && repositories.size() > 0) {
            currentRepository = repositories.get(0);
        }

        repositoriesModel = LoadableDetachableModel
            .of(() -> externalSearchService.listDocumentRepositories(project));

        mainContainer = new WebMarkupContainer("mainContainer");
        mainContainer.setOutputMarkupId(true);
        add(mainContainer);

        DocumentRepositorySelectionForm projectSelectionForm = new DocumentRepositorySelectionForm(
            "repositorySelectionForm");
        mainContainer.add(projectSelectionForm);

        SearchForm searchForm = new SearchForm("searchForm");
        add(searchForm);
        mainContainer.add(searchForm);

        List<IColumn<ExternalSearchResult, String>> columns = new ArrayList<>();

        columns.add(new AbstractColumn<ExternalSearchResult, String>(new Model<>("Results"))
        {
            private static final long serialVersionUID = -5658664083675871242L;

            @Override public void populateItem(Item<ICellPopulator<ExternalSearchResult>> cellItem,
                String componentId, IModel<ExternalSearchResult> model)
            {
                @SuppressWarnings("rawtypes") Item rowItem = cellItem.findParent(Item.class);
                int rowIndex = rowItem.getIndex();
                ResultRowView rowView = new ResultRowView(componentId, rowIndex + 1, model);
				
				if (getAnnotationPage().getModelObject().getDocument().getName()!=null) {
					String documentTitle=getAnnotationPage().getModelObject().getDocument().getName();
					if (documentTitle.equals(rowView.documentID)) {
						String documentTitle1="<mark>"+rowView.title+"</mark>";
						rowView.link.add(new Label("title",documentTitle1).setEscapeModelStrings(false));
					}else {
						rowView.link.add(new Label("title", rowView.title));
					}
				}
                cellItem.add(rowView);
            }
        });

        if (searchState.getDataProvider() == null) {
            searchState.setDataProvider(new ExternalResultDataProvider(externalSearchService,
                    userRepository.getCurrentUser()));
        }

        dataTableContainer = new WebMarkupContainer("dataTableContainer");
        dataTableContainer.setOutputMarkupId(true);
        mainContainer.add(dataTableContainer);

        DataTable<ExternalSearchResult, String> resultTable = new DefaultDataTable<>("resultsTable",
            columns, searchState.getDataProvider(), 8);
        resultTable.setCurrentPage(searchState.getCurrentPage());
        dataTableContainer.add(resultTable);
    }

    @Override
    protected void onDetach()
    {
        ExternalSearchUserState searchState = searchStateModel.getObject();

        // Save the current page number of the search results when the sidebar being switched
        DataTable<ExternalSearchResult, String> resultTable = 
                (DataTable<ExternalSearchResult, String>) dataTableContainer.get("resultsTable");
        searchState.setCurrentPage(resultTable.getCurrentPage());

        // save current repository
        searchState.setCurrentRepository(currentRepository);

        super.onDetach();
    }

    @OnEvent
    public void onRenderAnnotations(RenderAnnotationsEvent aEvent)
    {
        ExternalSearchUserState searchState = searchStateModel.getObject();
        
        // highlight keywords if a document is selected from result list
        // and it is the current document opened
        if (searchState.getSelectedResult() != null &&
            (searchState.getSelectedResult().getDocumentId().equals(
                getAnnotationPage().getModelObject().getDocument().getName()))) {
            highlightKeywords(aEvent.getState(), aEvent.getVDocument());
        } else {
            // a document was opened not by selecting from the result list
            searchState.setSelectedResult(null);
        }
    }

    private void highlightKeywords (AnnotatorState aAnnotatorState, VDocument aVDocument)
    {
        ExternalSearchUserState searchState = searchStateModel.getObject();
        try {
            String documentText = getCasProvider().get().getDocumentText();
            
            for (ExternalSearchHighlight highlight :
                    searchState.getSelectedResult().getHighlights()) {
                
                Optional<ExternalSearchHighlight> exHighlight = HighlightUtils
                        .parseHighlight(highlight.getHighlight(), documentText);
                if (exHighlight.isPresent()) {
                    // Highlight the keywords in the annotator indicated by the offsets
                    // if they are within the current window.
                    for (OffsetSpan offset : exHighlight.get().getOffsets()) {
                        if (aAnnotatorState.getWindowBeginOffset() <= offset.getBegin()) {
                            if (offset.getEnd() <= aAnnotatorState.getWindowEndOffset()) {
                                aVDocument.add(new VTextMarker(VMarker.MATCH_FOCUS,
                                        offset.getBegin() - aAnnotatorState.getWindowBeginOffset(),
                                        offset.getEnd() - aAnnotatorState.getWindowBeginOffset()));
                            }
                            else {
                                break;
                            }
                        }
                    }
                }
            }
        }
        catch (IOException e) {
            LOG.error("Unable to load document {}: {}",
                    searchState.getSelectedResult().getDocumentId(), e.getMessage(), e);
            error("Unable to load document " + searchState.getSelectedResult().getDocumentId() + ": "
                    + ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private void actionImport(AjaxRequestTarget aTarget, ExternalSearchResult aResult)
    {
        aTarget.addChildren(getPage(), IFeedback.class);
        searchStateModel.getObject().setSelectedResult(aResult);
        try {
            boolean imported = documentImporter.importDocumentFromDocumentRepository(
                    userRepository.getCurrentUser(), project, aResult.getCollectionId(),
                    aResult.getDocumentId(), currentRepository);
            
            if (imported) {
                success("Imported document: " + aResult.getDocumentId());
            }
            else {
                info("Document already present: " + aResult.getDocumentId());
            }

            getAnnotationPage().actionShowSelectedDocument(aTarget,
                    documentService.getSourceDocument(project, aResult.getDocumentId()));
        }
        catch (Exception e) {
            LOG.error("Unable to load document {}: {}", aResult.getDocumentId(), e.getMessage(), e);
            error("Unable to load document " + aResult.getDocumentId() + ": "
                    + ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private void actionOpen(AjaxRequestTarget aTarget, ExternalSearchResult aResult)
    {
        try {
            searchStateModel.getObject().setSelectedResult(aResult);
            getAnnotationPage().actionShowSelectedDocument(aTarget,
                    documentService.getSourceDocument(project, aResult.getDocumentId()));
        }
        catch (Exception e) {
            LOG.error("Unable to load document {}: {}", aResult.getDocumentId(), e.getMessage(), e);
            error("Unable to load document " + aResult.getDocumentId() + ": "
                    + ExceptionUtils.getRootCauseMessage(e));
            aTarget.addChildren(getPage(), IFeedback.class);
        }
    }

    private class DocumentRepositorySelectionForm
        extends Form<DocumentRepository>
    {
        private static final long serialVersionUID = 660903434919120494L;

        public DocumentRepositorySelectionForm(String aId)
        {
            super(aId);

            DropDownChoice<DocumentRepository> repositoryCombo =
                new BootstrapSelect<DocumentRepository>("repositoryCombo",
                new PropertyModel<DocumentRepository>(ExternalSearchAnnotationSidebar.this,
                    "currentRepository"), repositoriesModel);

            repositoryCombo.setChoiceRenderer(new ChoiceRenderer<DocumentRepository>("name"));
            repositoryCombo.setNullValid(false);

            // Just update the selection
            repositoryCombo.add(new LambdaAjaxFormComponentUpdatingBehavior("change"));
            add(repositoryCombo);

        }
    }

    private class SearchForm
        extends Form<Void>
    {
        private static final long serialVersionUID = -2787363313878650063L;

        public SearchForm(String id)
        {
            super(id);
            add(new TextField<>("queryInput", searchStateModel.bind("query"), String.class));
            LambdaAjaxSubmitLink searchLink = new LambdaAjaxSubmitLink("submitSearch",
                ExternalSearchAnnotationSidebar.this::actionSearch);
            add(searchLink);
            setDefaultButton(searchLink);
        }
    }

    private void actionSearch(AjaxRequestTarget aTarget, Form aForm)
    {
        ExternalSearchUserState searchState = searchStateModel.getObject();

        searchState.setSelectedResult(null);

        // No repository, no results
        if (currentRepository == null) {
            error("No repository selected");
            aTarget.addChildren(getPage(), IFeedback.class);
            return;
        }
        
        try {
            searchState.getDataProvider().searchDocuments(currentRepository,
                    searchState.getQuery());
        }
        catch (Exception e) {
            LOG.error("Unable to perform query", e);
            error("Unable to load data: " + ExceptionUtils.getRootCauseMessage(e));
            aTarget.addChildren(getPage(), IFeedback.class);
        }

        aTarget.add(dataTableContainer);

        applicationEventPublisher.get()
                .publishEvent(new ExternalSearchQueryEvent(this, currentRepository.getProject(),
                        userRepository.getCurrentUser().getUsername(), searchState.getQuery()));
    }

    public class ResultRowView
        extends Panel
    {
        private static final long serialVersionUID = 6212628948731147733L;
        private LambdaAjaxLink link;
		private String title;
		private String documentID;
        public ResultRowView(String id, long rowNumber, IModel<ExternalSearchResult> model)
        {
            super(id, model);

            ExternalSearchResult result = (ExternalSearchResult) getDefaultModelObject();
            
            boolean existsSourceDocument = documentService.existsSourceDocument(project,
                    result.getDocumentId());

            // Import and open annotation
            if (!existsSourceDocument) {
                link = new LambdaAjaxLink("docLink", t -> actionImport(t, result));
            }
            else {
                // open action
                link = new LambdaAjaxLink("docLink", t -> actionOpen(t, result));
            }

            title = defaultIfBlank(result.getDocumentTitle(),
                defaultIfBlank(result.getDocumentId(),
                    defaultIfBlank(result.getOriginalUri(), "<no title>")));
                documentID=result.getDocumentId();
            add(link);

             if ((getAnnotationPage().getModelObject().getDocument().getName())==null) {
				Label label = new Label("title", title);
				link.add(label);
			}
            link.add(new Label("score", result.getScore()));
            link.add(new Label("importStatus",
                () -> existsSourceDocument ? "imported" : "not imported"));

            // FIXME: Should display all highlights
            String highlight = "NO MATCH PREVIEW AVAILABLE";
            if (!result.getHighlights().isEmpty()) {
                highlight = Utilities.cleanHighlight(result.getHighlights().get(0).getHighlight());
            }
            link.add(new Label("highlight", highlight).setEscapeModelStrings(false));
        }
    }

    public static class ExternalSearchUserState
        implements Serializable
    {
        private static final long serialVersionUID = 366937089563292016L;

        private AnnotationLayer layer;

        private DocumentRepository currentRepository = null;

        private String query = null;

        private ExternalResultDataProvider dataProvider = null;

        private long currentPage = 1;

        private ExternalSearchResult selectedResult;

        public DocumentRepository getCurrentRepository()
        {
            return currentRepository;
        }

        public void setCurrentRepository(DocumentRepository aRepository)
        {
            currentRepository = aRepository;
        }

        public AnnotationLayer getLayer()
        {
            return layer;
        }

        public void setLayer(AnnotationLayer aAnnotationLayer)
        {
            layer = aAnnotationLayer;
        }

        public String getQuery()
        {
            return query;
        }

        public void setQuery(String aQuery)
        {
            query = aQuery;
        }

        public ExternalResultDataProvider getDataProvider()
        {
            return dataProvider;
        }

        public void setDataProvider(ExternalResultDataProvider aDataProvider)
        {
            dataProvider = aDataProvider;
        }

        public long getCurrentPage()
        {
            return currentPage;
        }

        public void setCurrentPage(long aCurrentPage)
        {
            currentPage = aCurrentPage;
        }

        public ExternalSearchResult getSelectedResult()
        {
            return selectedResult;
        }

        public void setSelectedResult(ExternalSearchResult aSelectedResult)
        {
            selectedResult = aSelectedResult;
        }
    }
}
