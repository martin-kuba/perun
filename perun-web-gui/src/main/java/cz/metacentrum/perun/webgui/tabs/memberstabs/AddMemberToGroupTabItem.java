package cz.metacentrum.perun.webgui.tabs.memberstabs;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.client.ui.*;
import cz.metacentrum.perun.webgui.client.PerunWebSession;
import cz.metacentrum.perun.webgui.client.UiElements;
import cz.metacentrum.perun.webgui.client.localization.ButtonTranslation;
import cz.metacentrum.perun.webgui.client.localization.WidgetTranslation;
import cz.metacentrum.perun.webgui.client.mainmenu.MainMenu;
import cz.metacentrum.perun.webgui.client.resources.*;
import cz.metacentrum.perun.webgui.json.GetEntityById;
import cz.metacentrum.perun.webgui.json.JsonCallbackEvents;
import cz.metacentrum.perun.webgui.json.JsonUtils;
import cz.metacentrum.perun.webgui.json.groupsManager.AddMember;
import cz.metacentrum.perun.webgui.json.membersManager.FindCompleteRichMembers;
import cz.metacentrum.perun.webgui.model.Group;
import cz.metacentrum.perun.webgui.model.RichMember;
import cz.metacentrum.perun.webgui.tabs.MembersTabs;
import cz.metacentrum.perun.webgui.tabs.TabItem;
import cz.metacentrum.perun.webgui.tabs.TabItemWithUrl;
import cz.metacentrum.perun.webgui.tabs.UrlMapper;
import cz.metacentrum.perun.webgui.widgets.CustomButton;
import cz.metacentrum.perun.webgui.widgets.ExtendedTextBox;
import cz.metacentrum.perun.webgui.widgets.TabMenu;

import java.util.ArrayList;
import java.util.Map;

/**
 * Add member to group page
 *
 * @author Vaclav Mach <374430@mail.muni.cz>
 * @author Pavel Zlamal <256627@mail.muni.cz>
 */
public class AddMemberToGroupTabItem implements TabItem, TabItemWithUrl {

    /**
     * Perun web session
     */
    private PerunWebSession session = PerunWebSession.getInstance();

    /**
     * Content widget - should be simple panel
     */
    private SimplePanel contentWidget = new SimplePanel();

    /**
     * Title widget
     */
    private Label titleWidget = new Label("Loading group");

    // data
    private Group group;

    // when searching
    private String searchString = "";

    private int groupId;
    private ArrayList<RichMember> alreadyAddedList = new ArrayList<RichMember>();
    private SimplePanel alreadyAdded = new SimplePanel();

    /**
     * Creates a tab instance
     *
     * @param group
     */
    public AddMemberToGroupTabItem(Group group){
        this.group = group;
        this.groupId = group.getId();
    }

    /**
     * Creates a tab instance
     *
     * @param groupId
     */
    public AddMemberToGroupTabItem(int groupId){
        this.groupId = groupId;
        JsonCallbackEvents events = new JsonCallbackEvents(){
            public void onFinished(JavaScriptObject jso) {
                group = jso.cast();
            }
        };
        new GetEntityById(PerunEntity.GROUP, groupId, events).retrieveData();
    }


    public boolean isPrepared(){
        return !(group == null);
    }

    public Widget draw() {

        titleWidget.setText(Utils.getStrippedStringWithEllipsis(group.getName()) + ": add member");

        // MAIN PANEL
        final VerticalPanel firstTabPanel = new VerticalPanel();
        firstTabPanel.setSize("100%", "100%");

        boolean isMembersGroup = group.isCoreGroup();

        // if members or admins group, hide
        if(isMembersGroup){

            firstTabPanel.add(new HTML("<p>Group \""+group.getName()+"\" can't have members managed from Group admin. Please use VO admin section.</p>"));

            this.contentWidget.setWidget(firstTabPanel);
            return getWidget();

        }

        // MENU
        TabMenu tabMenu = new TabMenu();
        firstTabPanel.add(tabMenu);
        firstTabPanel.setCellHeight(tabMenu, "30px");

        // CALLBACKS
        final FindCompleteRichMembers findMembers;

        // elements handled by callback events
        final CustomButton searchButton = TabMenu.getPredefinedButton(ButtonType.SEARCH, ButtonTranslation.INSTANCE.searchMemberInParentGroup());
        final CheckBox disabled = new CheckBox(WidgetTranslation.INSTANCE.showDisabledMembers());

        // search through whole VO
        findMembers = new FindCompleteRichMembers(PerunEntity.VIRTUAL_ORGANIZATION, group.getVoId(), "", null);
        findMembers.setCustomEmptyTableMessage("Search for members to add by name, login or email.");
        findMembers.setEvents(JsonCallbackEvents.mergeEvents(JsonCallbackEvents.disableButtonEvents(searchButton, JsonCallbackEvents.disableCheckboxEvents(disabled)),
                new JsonCallbackEvents(){
                    @Override
                    public void onFinished(JavaScriptObject jso) {
                        // if found 1 item, select
                        ArrayList<RichMember> list = JsonUtils.jsoAsList(jso);
                        if (list != null && list.size() == 1) {
                            findMembers.getSelectionModel().setSelected(list.get(0), true);
                        }
                    }
                }));

        // ADD
        final CustomButton addButton = TabMenu.getPredefinedButton(ButtonType.ADD, ButtonTranslation.INSTANCE.addSelectedMemberToGroup());
        // close tab event
        final TabItem tab = this;

        // DISABLED CHECKBOX
        disabled.setTitle(WidgetTranslation.INSTANCE.showDisabledMembersTitle());

        // checkbox click handler
        disabled.addClickHandler(new ClickHandler(){
            public void onClick(ClickEvent event) {
                findMembers.excludeDisabled(!disabled.getValue());
                if (!searchString.equalsIgnoreCase("")) {
                    findMembers.clearTable();
                    findMembers.retrieveData();
                }
            }
        });

        // SEARCH FOR BUTTON
        final ExtendedTextBox searchBox = tabMenu.addSearchWidget(new PerunSearchEvent() {
            public void searchFor(String text) {
                searchString = text;
                findMembers.excludeDisabled(!disabled.getValue());
                if (!searchString.equalsIgnoreCase("")) {
                    findMembers.searchFor(searchString);
                }
            }
        }, searchButton);
        searchBox.getTextBox().setText(searchString);

        // click handler
        addButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                // state specific events
                final ArrayList<RichMember> membersToAdd = findMembers.getTableSelectedList();
                if (UiElements.cantSaveEmptyListDialogBox(membersToAdd)) {
                    // TODO - SHOULD HAVE ONLY ONE CALLBACK TO CORE !!
                    for (int i=0; i<membersToAdd.size(); i++ ) {
                        final int n = i;
                        AddMember request = new AddMember(JsonCallbackEvents.disableButtonEvents(addButton, new JsonCallbackEvents(){
                            @Override
                            public void onFinished(JavaScriptObject jso) {
                                // unselect added person
                                findMembers.getSelectionModel().setSelected(membersToAdd.get(n), false);
                                // put names to already added
                                alreadyAddedList.add(membersToAdd.get(n));
                                rebuildAlreadyAddedWidget();
                                // clear search
                                searchBox.getTextBox().setText("");
                            }
                        }));
                        request.addMemberToGroup(group, membersToAdd.get(i));
                    }
                }
            }
        });

        tabMenu.addWidget(addButton);

        tabMenu.addWidget(TabMenu.getPredefinedButton(ButtonType.CLOSE, "", new ClickHandler() {
            @Override
            public void onClick(ClickEvent clickEvent) {
                // with refresh if somebody was added
                session.getTabManager().closeTab(tab, !alreadyAddedList.isEmpty());
            }
        }));

        tabMenu.addWidget(disabled);
        rebuildAlreadyAddedWidget();
        firstTabPanel.add(alreadyAdded);

        ScrollPanel tableWrapper = new ScrollPanel();

        CellTable<RichMember> table = findMembers.getEmptyTable(new FieldUpdater<RichMember, RichMember>() {
            // when user click on a row -> open new tab
            public void update(int index, RichMember object, RichMember value) {
                session.getTabManager().addTab(new MemberDetailTabItem(object.getId(), groupId));
            }
        });

        addButton.setEnabled(false);
        JsonUtils.addTableManagedButton(findMembers, table, addButton);

        table.addStyleName("perun-table");
        tableWrapper.setWidget(table);
        tableWrapper.addStyleName("perun-tableScrollPanel");

        session.getUiElements().resizeSmallTabPanel(tableWrapper, 350, this);

        if (disabled.getValue()) {
            findMembers.excludeDisabled(false);
        } else {
            findMembers.excludeDisabled(true);
        }

        // if not empty - start searching
        if (!searchString.equalsIgnoreCase("")) {
            findMembers.searchFor(searchString);
        }

        firstTabPanel.add(tableWrapper);

        this.contentWidget.setWidget(firstTabPanel);
        return getWidget();

    }

    /**
     * Rebuild already added widget based on already added members
     */
    private void rebuildAlreadyAddedWidget() {

        alreadyAdded.setStyleName("alreadyAdded");
        alreadyAdded.setVisible(!alreadyAddedList.isEmpty());
        alreadyAdded.setWidget(new HTML("<strong>Already added: </strong>"));
        for (int i=0; i<alreadyAddedList.size(); i++) {
            alreadyAdded.getWidget().getElement().setInnerHTML(alreadyAdded.getWidget().getElement().getInnerHTML()+ ((i!=0) ? ", " : "") + alreadyAddedList.get(i).getUser().getFullName());
        }
    }

    public Widget getWidget() {
        return this.contentWidget;
    }

    public Widget getTitle() {
        return this.titleWidget;
    }

    public ImageResource getIcon() {
        return SmallIcons.INSTANCE.addIcon();
    }

    @Override
    public int hashCode() {
        final int prime = 59;
        int result = 1;
        result = prime * result + groupId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AddMemberToGroupTabItem other = (AddMemberToGroupTabItem) obj;
        if (groupId != other.groupId)
            return false;
        return true;
    }

    public boolean multipleInstancesEnabled() {
        return false;
    }

    public void open() {
        session.getUiElements().getMenu().openMenu(MainMenu.GROUP_ADMIN);
        if(group != null){
            session.setActiveGroup(group);
            return;
        }
        session.setActiveGroupId(groupId);
    }

    public boolean isAuthorized() {

        if (session.isVoAdmin(group.getVoId()) || session.isGroupAdmin(groupId)) {
            return true;
        } else {
            return false;
        }

    }

    public final static String URL = "add-to-grp";

    public String getUrl() {
        return URL;
    }

    public String getUrlWithParameters() {
        return MembersTabs.URL + UrlMapper.TAB_NAME_SEPARATOR + getUrl() + "?grp=" + groupId;
    }

    static public AddMemberToGroupTabItem load(Map<String, String> parameters) {
        int gid = Integer.parseInt(parameters.get("grp"));
        return new AddMemberToGroupTabItem(gid);
    }

}