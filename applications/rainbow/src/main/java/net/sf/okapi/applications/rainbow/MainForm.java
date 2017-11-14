/*===========================================================================
  Copyright (C) 2008-2012 by the Okapi Framework contributors
-----------------------------------------------------------------------------
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
===========================================================================*/

package net.sf.okapi.applications.rainbow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;

import net.sf.okapi.applications.rainbow.batchconfig.BatchConfiguration;
import net.sf.okapi.applications.rainbow.lib.CodeFinderEditor;
import net.sf.okapi.applications.rainbow.lib.EncodingItem;
import net.sf.okapi.applications.rainbow.lib.EncodingManager;
import net.sf.okapi.applications.rainbow.lib.FormatManager;
import net.sf.okapi.applications.rainbow.lib.ILog;
import net.sf.okapi.applications.rainbow.lib.LanguageItem;
import net.sf.okapi.applications.rainbow.lib.LanguageManager;
import net.sf.okapi.applications.rainbow.lib.LogForm;
import net.sf.okapi.applications.rainbow.lib.PathBuilderPanel;
import net.sf.okapi.applications.rainbow.lib.Utils;
import net.sf.okapi.applications.rainbow.logger.ILogHandler;
import net.sf.okapi.applications.rainbow.logger.LogHandlerFactory;
import net.sf.okapi.applications.rainbow.pipeline.BOMConversionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.BatchTranslationPipeline;
import net.sf.okapi.applications.rainbow.pipeline.CharListingPipeline;
import net.sf.okapi.applications.rainbow.pipeline.EncodingConversionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.FormatConversionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.IPredefinedPipeline;
import net.sf.okapi.applications.rainbow.pipeline.ImageModificationPipeline;
import net.sf.okapi.applications.rainbow.pipeline.ImportTMPipeline;
import net.sf.okapi.applications.rainbow.pipeline.LineBreakConversionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.PipelineEditor;
import net.sf.okapi.applications.rainbow.pipeline.PipelineWrapper;
import net.sf.okapi.applications.rainbow.pipeline.QualityCheckPipeline;
import net.sf.okapi.applications.rainbow.pipeline.RTFConversionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.SnRWithFilterPipeline;
import net.sf.okapi.applications.rainbow.pipeline.SnRWithoutFilterPipeline;
import net.sf.okapi.applications.rainbow.pipeline.TermExtractionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.TextRewritingPipeline;
import net.sf.okapi.applications.rainbow.pipeline.TranslationComparisonPipeline;
import net.sf.okapi.applications.rainbow.pipeline.TranslationKitCreationPipeline;
import net.sf.okapi.applications.rainbow.pipeline.TranslationKitPostProcessingPipeline;
import net.sf.okapi.applications.rainbow.pipeline.URIConversionPipeline;
import net.sf.okapi.applications.rainbow.pipeline.XMLAnalysisPipeline;
import net.sf.okapi.applications.rainbow.pipeline.XMLCharactersFixingPipeline;
import net.sf.okapi.applications.rainbow.pipeline.XMLValidationPipeline;
import net.sf.okapi.applications.rainbow.pipeline.XSLTransformPipeline;
import net.sf.okapi.common.ExecutionContext;
import net.sf.okapi.common.LocaleId;
import net.sf.okapi.common.UserConfiguration;
import net.sf.okapi.common.Util;
import net.sf.okapi.common.exceptions.OkapiException;
import net.sf.okapi.common.filters.DefaultFilters;
import net.sf.okapi.common.filters.FilterConfigurationMapper;
import net.sf.okapi.common.plugins.PluginsManager;
import net.sf.okapi.common.resource.RawDocument;
import net.sf.okapi.common.ui.AboutDialog;
import net.sf.okapi.common.ui.BaseHelp;
import net.sf.okapi.common.ui.CharacterInfoDialog;
import net.sf.okapi.common.ui.Dialogs;
import net.sf.okapi.common.ui.InputDialog;
import net.sf.okapi.common.ui.MRUList;
import net.sf.okapi.common.ui.ResourceManager;
import net.sf.okapi.common.ui.UIUtil;
import net.sf.okapi.common.ui.filters.FilterConfigurationsDialog;
import net.sf.okapi.common.ui.plugins.PluginsManagerDialog;
import net.sf.okapi.filters.rainbowkit.Manifest;
import net.sf.okapi.filters.rainbowkit.ui.ManifestDialog;
import net.sf.okapi.lib.ui.editor.PairEditorUserTest;
import net.sf.okapi.lib.ui.segmentation.SRXEditor;
import net.sf.okapi.lib.ui.translation.DefaultConnectors;
import net.sf.okapi.lib.ui.verification.QualityCheckEditor;
import net.sf.okapi.lib.verification.IQualityCheckEditor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

public class MainForm { //implements IParametersProvider {
	
	public static final String APPNAME = "Rainbow"; //$NON-NLS-1$

	public static final String OPT_ALLOWDUPINPUT = "allowDupInput"; //$NON-NLS-1$
	public static final String OPT_LOADMRU = "loadMRU"; //$NON-NLS-1$
	public static final String OPT_BOUNDS = "bounds"; //$NON-NLS-1$
	public static final String OPT_LOGLEVEL = "logLevel"; //$NON-NLS-1$
	public static final String OPT_ALWAYSOPENLOG = "alwaysOpenLog"; //$NON-NLS-1$
	public static final String OPT_DROPINSDIR = "dropinsDir"; //$NON-NLS-1$
	public static final String OPT_PARAMSDIR = "paramsDir"; //$NON-NLS-1$
	public static final String OPT_USEUSERDEFAULTS = "useUserDefaults"; //$NON-NLS-1$
	public static final String OPT_SOURCELOCALE = "sourceLocale"; //$NON-NLS-1$
	public static final String OPT_SOURCEENCODING = "sourceEncoding"; //$NON-NLS-1$
	public static final String OPT_TARGETLOCALE = "targetLocale"; //$NON-NLS-1$
	public static final String OPT_TARGETENCODING = "targetEncoding"; //$NON-NLS-1$
	
	protected static final String PRJPIPELINEID = "currentProjectPipeline"; //$NON-NLS-1$
	protected static final String NOEXPAND_EXTENSIONS = ";.pentm;"; //$NON-NLS-1$

	private static final String HELP_USAGE = "Rainbow - Usage"; //$NON-NLS-1$
	
	private int currentInput;
	private ArrayList<Table> inputTables;
	private ArrayList<InputTableModel> inputTableMods;
	private Shell shell;
	private ILog log;
	private ILogHandler logHandler;
	private UserConfiguration config;
	private MRUList mruList;
	private String appRootFolder;
	private String sharedFolder;
	private BaseHelp help;
	private Project prj;
	private PluginsManager pm;
	private PipelineWrapper wrapper; 
	private StatusBar statusBar;
	private TabFolder tabFolder;
	private Label stInputRoot;
	private Text edInputRoot;
	private Button btGetRoot;
	private Button chkUseOutputRoot;
	private Text edOutputRoot;
	private Text edSourceLang;
	private List lbSourceLang;
	private boolean inSourceLangSelection;
	private Text edSourceEnc;
	private List lbSourceEnc;
	private boolean inSourceEncSelection;
	private Text edTargetLang;
	private List lbTargetLang;
	private boolean inTargetLangSelection;
	private Text edTargetEnc;
	private List lbTargetEnc;
	private boolean inTargetEncSelection;
	private Button chkUseCustomParametersFolder;
	private Text edParamsFolder;
	private boolean customFilterConfigsNeedUpdate;
	private Button btGetParamsFolder; 
	private TabItem tiInputList1;
	private TabItem tiInputList2;
	private TabItem tiInputList3;
	private TabItem tiOptions;
	private PathBuilderPanel pnlPathBuilder;
	private int waitCount;
	private boolean startLogWasRequested;
	private LanguageManager lm;
	private ResourceManager rm;
	private FormatManager fm;
	private FilterConfigurationMapper fcMapper;
	private EncodingManager em;
	private UtilitiesAccess utilitiesAccess;
	private UtilityDriver ud;
	private MenuItem miInput;
	private MenuItem miUtilities;
	private MenuItem miHelp;
	private MenuItem miSave;
	private MenuItem miTools;
	private MenuItem miEditInputProperties;
	private MenuItem cmiEditInputProperties;
	private MenuItem miOpenInputDocument;
	private MenuItem cmiOpenInputDocument;
	private MenuItem miCreateInputDocument;
	private MenuItem cmiCreateInputDocument;
	private MenuItem miRemoveInputDocuments;
	private MenuItem cmiRemoveInputDocuments;
	private MenuItem miMoveDocumentsUp;
	private MenuItem cmiMoveDocumentsUp;
	private MenuItem miMoveDocumentsDown;
	private MenuItem cmiMoveDocumentsDown;
	private MenuItem miOpenFolder;
	private MenuItem cmiOpenFolder;
	private MenuItem miOpenOutputFolder;
	private MenuItem miMRU;
	private ToolItem tbiMoveUp;
	private ToolItem tbiMoveDown;
	private ToolItem tbiAddDocs;
	private ToolItem tbiOpenFolder;
	private ToolItem tbiEditDocProp;
	private ExecutionContext context;

	public MainForm (Shell shell,
		String projectFile)
	{
		try {
			this.shell = shell;
			
			setDirectories();
			loadResources();

			config = new UserConfiguration();
			config.setProperty(OPT_LOADMRU, "0"); // Defaults //$NON-NLS-1$
			config.load(APPNAME); // Load the current user preferences
			mruList = new MRUList(9);
			mruList.getFromProperties(config);
			
			context = new ExecutionContext();
			context.setApplicationName("Rainbow");
			context.setUiParent(shell);
			
			createContent();
			createProject(false);
			
			// Check for -? and -h parameters first
			if (( projectFile != null ) && ( "-?".equals(projectFile) || "-h".equals(projectFile) )) {
				projectFile = null;
				Util.openWikiTopic(HELP_USAGE);
			}

			if ( projectFile != null ) {
				// Load project if passed as parameter
				openProject(projectFile);
			}
			else { // Load MRU project if requested
				int n = config.getInteger(OPT_LOADMRU); //$NON-NLS-1$
				if ( n > 0 ) { // 1=ask, 2=load without asking
					String path = mruList.getfirst();
					if ( path != null ) {
						if ( n == 1 ) { // Ask
							// Ask confirmation
							MessageBox dlg = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
							dlg.setMessage(Res.getString("MainForm.askMRU1")+path+Res.getString("MainForm.askMRU2")); //$NON-NLS-1$ //$NON-NLS-2$
							dlg.setText(APPNAME);
							if ( dlg.open() == SWT.YES ) n = 2;
						}
						if ( n == 2 ) openProject(path);
					}
				}
			}
		}
		catch ( Throwable e ) {
			Dialogs.showError(shell, e.getMessage(), null);
			//throw new OkapiException("Error in MainForm.", e); 
		}
	}

	public String getDropinsDirectory () {
		String tmp = config.getProperty(OPT_DROPINSDIR, "");
		if ( tmp.endsWith("/") || tmp.endsWith("\\") ) {
			tmp = tmp.substring(0, tmp.length()-1);
		}
		if ( tmp.length() > 0 ) return tmp;
		// Else: Use default
		return appRootFolder+File.separator+"dropins";
	}
	
	private void createContent ()
		throws Exception
	{
		GridLayout layTmp = new GridLayout(3, false);
		shell.setLayout(layTmp);
		shell.setImages(rm.getImages("rainbow")); //$NON-NLS-1$
		
		// Handling of the closing event
		shell.addShellListener(new ShellListener() {
			public void shellActivated(ShellEvent event) {}
			public void shellClosed(ShellEvent event) {
				saveUserConfiguration();
				if ( !canContinue(false) ) event.doit = false;
			}
			public void shellDeactivated(ShellEvent event) {}
			public void shellDeiconified(ShellEvent event) {}
			public void shellIconified(ShellEvent event) {}
		});

		log = new LogForm(shell);
		log.setTitle(Res.getString("LOG_CAPTION")); //$NON-NLS-1$

		logHandler = LogHandlerFactory.getLogHandler();
		logHandler.initialize(log);
		setLogLevel();

		fcMapper = new FilterConfigurationMapper();
		// Get pre-defined configurations
		DefaultFilters.setMappings(fcMapper, false, true);
		// Discover and add plug-ins
		pm = new PluginsManager();
		updatePluginsAndDependencies();

		// Toolbar
		createToolbar();
		
		// Menus
	    Menu menuBar = new Menu(shell, SWT.BAR);
		shell.setMenuBar(menuBar);

		// File menu
		MenuItem topItem = new MenuItem(menuBar, SWT.CASCADE);
		topItem.setText(rm.getCommandLabel("file")); //$NON-NLS-1$
		Menu dropMenu = new Menu(shell, SWT.DROP_DOWN);
		topItem.setMenu(dropMenu);

		MenuItem menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "file.new"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				createProject(true);
            }
		});
		menuItem.setImage(rm.getImage("newproject")); //$NON-NLS-1$

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "file.open"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				openProject(null);
            }
		});
		menuItem.setImage(rm.getImage("openproject")); //$NON-NLS-1$

		new MenuItem(dropMenu, SWT.SEPARATOR);

		miSave = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miSave, "file.save"); //$NON-NLS-1$
		miSave.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
            	saveProject(prj.path);
            }
		});
		miSave.setImage(rm.getImage("saveproject")); //$NON-NLS-1$
		
		miSave = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miSave, "file.saveas"); //$NON-NLS-1$
		miSave.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
            	saveProject(null);
            }
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);

		miMRU = new MenuItem(dropMenu, SWT.CASCADE);
		rm.setCommand(miMRU, "file.mru"); //$NON-NLS-1$
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "file.clearmru"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
            	clearMRU();
            }
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "file.userpref"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				editUserPreferences();
            }
		});

		new MenuItem(dropMenu, SWT.SEPARATOR);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "file.exit"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				shell.close();
            }
		});

		// View menu
		topItem = new MenuItem(menuBar, SWT.CASCADE);
		topItem.setText(rm.getCommandLabel("view")); //$NON-NLS-1$
		dropMenu = new Menu(shell, SWT.DROP_DOWN);
		topItem.setMenu(dropMenu);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "view.inputList1"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				tabFolder.setSelection(0);
				updateTabInfo();
            }
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "view.inputList2"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				tabFolder.setSelection(1);
				updateTabInfo();
            }
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "view.inputList3"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				tabFolder.setSelection(2);
				updateTabInfo();
            }
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "view.langAndEnc"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				tabFolder.setSelection(3);
				updateTabInfo();
            }
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "view.otherSettings"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				tabFolder.setSelection(4);
				updateTabInfo();
            }
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "view.log"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if ( log.isVisible() ) log.hide();
				else log.show();
            }
		});
		menuItem.setImage(rm.getImage("log")); //$NON-NLS-1$
		
		// Input menu
		miInput = new MenuItem(menuBar, SWT.CASCADE);
		miInput.setText(rm.getCommandLabel("input")); //$NON-NLS-1$
		dropMenu = new Menu(shell, SWT.DROP_DOWN);
		miInput.setMenu(dropMenu);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "input.addDocuments"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				addDocumentsFromList(null);
            }
		});
		menuItem.setImage(rm.getImage("addinput")); //$NON-NLS-1$
		
		miRemoveInputDocuments = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miRemoveInputDocuments, "input.removeDocuments"); //$NON-NLS-1$
		miRemoveInputDocuments.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				removeDocuments(-1);
            }
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "input.editRoot"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				changeRoot();
            }
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		miCreateInputDocument = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miCreateInputDocument, "input.createDocument"); //$NON-NLS-1$
		miCreateInputDocument.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				createDocument();
            }
		});
		
		miOpenInputDocument = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miOpenInputDocument, "input.openDocument"); //$NON-NLS-1$
		miOpenInputDocument.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				openDocument(-1);
            }
		});
		
		miOpenFolder = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miOpenFolder, "input.openFolder"); //$NON-NLS-1$
		miOpenFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				openContainingFolder(-1);
            }
		});
		miOpenFolder.setImage(rm.getImage("openfolder")); //$NON-NLS-1$
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		miMoveDocumentsUp = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miMoveDocumentsUp, "input.moveDocumentsUp"); //$NON-NLS-1$
		miMoveDocumentsUp.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				moveDocumentsUp();
			}
		});
		miMoveDocumentsUp.setImage(rm.getImage("moveup")); //$NON-NLS-1$
		
		
		miMoveDocumentsDown = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miMoveDocumentsDown, "input.moveDocumentsDown"); //$NON-NLS-1$
		miMoveDocumentsDown.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				moveDocumentsDown();
			}
		});
		miMoveDocumentsDown.setImage(rm.getImage("movedown")); //$NON-NLS-1$

		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		miEditInputProperties = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miEditInputProperties, "input.editProperties"); //$NON-NLS-1$
		miEditInputProperties.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				editInputProperties(-1);
			}
		});
		miEditInputProperties.setImage(rm.getImage("properties")); //$NON-NLS-1$
		
		// Utilities menu
		miUtilities = new MenuItem(menuBar, SWT.CASCADE);
		miUtilities.setText(rm.getCommandLabel("utilities")); //$NON-NLS-1$
		buildUtilitiesMenu();

		// Tools menu
		miTools = new MenuItem(menuBar, SWT.CASCADE);
		miTools.setText(rm.getCommandLabel("tools")); //$NON-NLS-1$
		dropMenu = new Menu(shell, SWT.DROP_DOWN);
		miTools.setMenu(dropMenu);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.editsegrules"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				editSegmentationRules(null);
			}
		});
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.qualitycheck"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				runQualityChecker();
			}
		});
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.codefindereditor"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				showCodeFinderEditor();
			}
		});
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.listencodings"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				listEncodings();
			}
		});
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.charinfo"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				showCharInfo();
			}
		});

		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.filterconfigurations"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				filterConfigurations();
			}
		});

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.pluginsmanager"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				pluginsManager();
			}
		});

		new MenuItem(dropMenu, SWT.SEPARATOR);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.exportbatchconfig"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				exportBatchConfiguration();
			}
		});

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "tools.installbatchconfig"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				installBatchConfiguration();
			}
		});

		//=== For user test
		new MenuItem(dropMenu, SWT.SEPARATOR);
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		menuItem.setText("For Testing...");
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				runTestingConsole();
			}
		});
		//=== end of block for user test

		// Help menu
		miHelp = new MenuItem(menuBar, SWT.CASCADE);
		miHelp.setText(rm.getCommandLabel("help")); //$NON-NLS-1$
		dropMenu = new Menu(shell, SWT.DROP_DOWN);
		miHelp.setMenu(dropMenu);

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.topics"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if ( help != null ) help.showWiki("Rainbow"); //$NON-NLS-1$
			}
		});
		menuItem.setImage(rm.getImage("help")); //$NON-NLS-1$

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.howtouse"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if ( help != null ) help.showWiki(HELP_USAGE); //$NON-NLS-1$
			}
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.update"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				UIUtil.start("http://okapiframework.org/updates.html?"  //$NON-NLS-1$
					+ getClass().getPackage().getImplementationTitle()
					+ "=" //$NON-NLS-1$
					+ getClass().getPackage().getImplementationVersion());
			}
		});

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.feedback"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				UIUtil.start("mailto:okapitools@opentag.com&subject=Feedback (Rainbow)"); //$NON-NLS-1$
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.bugreport"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				UIUtil.start("https://bitbucket.org/okapiframework/okapi/issues"); //$NON-NLS-1$
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.featurerequest"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				UIUtil.start("https://bitbucket.org/okapiframework/okapi/issues"); //$NON-NLS-1$
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.users"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				UIUtil.start("http://groups.yahoo.com/group/okapitools/"); //$NON-NLS-1$
			}
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "help.about"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				AboutDialog dlg = new AboutDialog(shell,
					Res.getString("MainForm.aboutCaption"), //$NON-NLS-1$
					Res.getString("MainForm.aboutAppName"), //$NON-NLS-1$
					getClass().getPackage().getImplementationVersion());
				dlg.showDialog();
			}
		});

		
		// Drop target for opening project
		DropTarget dropTarget = new DropTarget(shell, DND.DROP_DEFAULT | DND.DROP_COPY | DND.DROP_MOVE);
		dropTarget.setTransfer(new FileTransfer[]{FileTransfer.getInstance()}); 
		dropTarget.addDropListener(new DropTargetAdapter() {
			public void drop (DropTargetEvent e) {
				FileTransfer FT = FileTransfer.getInstance();
				if ( FT.isSupportedType(e.currentDataType) ) {
					String[] paths = (String[])e.data;
					if ( paths != null ) {
						openProject(paths[0]);
					}
				}
			}
		});
		
		// Root panel
		stInputRoot = new Label(shell, SWT.NONE);
		// Text an approximative text for correct sizing
		stInputRoot.setText(Res.getString("MainForm.inputRootSizingLabel")); //$NON-NLS-1$
		
		edInputRoot = new Text(shell, SWT.SINGLE | SWT.BORDER);
		edInputRoot.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		edInputRoot.setEditable(false);
		
		btGetRoot = new Button(shell, SWT.PUSH);
		btGetRoot.setText("..."); //$NON-NLS-1$
		btGetRoot.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				changeRoot();
			}
		});
		
		// Tab control
		tabFolder = new TabFolder(shell, SWT.NONE);
		GridData gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 3;
		tabFolder.setLayoutData(gdTmp);
		// Events are set at the end of this methods

		inputTables = new ArrayList<Table>(1);
		inputTableMods = new ArrayList<InputTableModel>();
		
		// Input List 1
		Composite comp = new Composite(tabFolder, SWT.NONE);
		comp.setLayout(new GridLayout());
		tiInputList1 = new TabItem(tabFolder, SWT.NONE);
		tiInputList1.setText(Res.getString("tiInputList1")); //$NON-NLS-1$
		tiInputList1.setControl(comp);
		buildInputTab(0, comp);
		
		// Input List 2
		comp = new Composite(tabFolder, SWT.NONE);
		comp.setLayout(new GridLayout());
		tiInputList2 = new TabItem(tabFolder, SWT.NONE);
		tiInputList2.setText(Res.getString("tiInputList2")); //$NON-NLS-1$
		tiInputList2.setControl(comp);
		buildInputTab(1, comp);
		
		// Input List 3
		comp = new Composite(tabFolder, SWT.NONE);
		comp.setLayout(new GridLayout());
		tiInputList3 = new TabItem(tabFolder, SWT.NONE);
		tiInputList3.setText(Res.getString("tiInputList3")); //$NON-NLS-1$
		tiInputList3.setControl(comp);
		buildInputTab(2, comp);
		
		// Context menu for the input list
		Menu inputTableMenu = new Menu(shell, SWT.POP_UP);
		
		menuItem = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(menuItem, "input.addDocuments"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				addDocumentsFromList(null);
            }
		});
		menuItem.setImage(rm.getImage("addinput")); //$NON-NLS-1$
		
		cmiRemoveInputDocuments = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiRemoveInputDocuments, "input.removeDocuments"); //$NON-NLS-1$
		cmiRemoveInputDocuments.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				removeDocuments(-1);
            }
		});
		
		new MenuItem(inputTableMenu, SWT.SEPARATOR);
		
		cmiCreateInputDocument = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiCreateInputDocument, "input.createDocument"); //$NON-NLS-1$
		cmiCreateInputDocument.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				createDocument();
            }
		});
		
		
		cmiOpenInputDocument = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiOpenInputDocument, "input.openDocument"); //$NON-NLS-1$
		cmiOpenInputDocument.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				openDocument(-1);
            }
		});
		
		cmiOpenFolder = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiOpenFolder, "input.openFolder"); //$NON-NLS-1$
		cmiOpenFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				openContainingFolder(-1);
            }
		});
		cmiOpenFolder.setImage(rm.getImage("openfolder")); //$NON-NLS-1$
		
		new MenuItem(inputTableMenu, SWT.SEPARATOR);
		
		cmiMoveDocumentsUp = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiMoveDocumentsUp, "input.moveDocumentsUp"); //$NON-NLS-1$
		cmiMoveDocumentsUp.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				moveDocumentsUp();
			}
		});
		cmiMoveDocumentsUp.setImage(rm.getImage("moveup")); //$NON-NLS-1$
		
		cmiMoveDocumentsDown = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiMoveDocumentsDown, "input.moveDocumentsDown"); //$NON-NLS-1$
		cmiMoveDocumentsDown.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				moveDocumentsDown();
			}
		});
		cmiMoveDocumentsDown.setImage(rm.getImage("movedown")); //$NON-NLS-1$

		new MenuItem(inputTableMenu, SWT.SEPARATOR);
		
		cmiEditInputProperties = new MenuItem(inputTableMenu, SWT.PUSH);
		rm.setCommand(cmiEditInputProperties, "input.editProperties"); //$NON-NLS-1$
		cmiEditInputProperties.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				editInputProperties(-1);
			}
		});
		cmiEditInputProperties.setImage(rm.getImage("properties")); //$NON-NLS-1$

		// Set the popup menus for the input lists
		inputTables.get(0).setMenu(inputTableMenu);
		inputTables.get(1).setMenu(inputTableMenu);
		inputTables.get(2).setMenu(inputTableMenu);
		
		// Pop up the property editor by double-clicking
		MouseAdapter ma = new MouseAdapter(){
			public void mouseDoubleClick(MouseEvent e) {
				Table t = (Table) e.getSource();				
				if (( e.x > t.getColumn(0).getWidth() )
					&& ( e.x < t.getColumn(0).getWidth()+t.getColumn(1).getWidth() )) {
					editInputProperties(-1);
				}
			}
		};
		inputTables.get(0).addMouseListener(ma);
		inputTables.get(1).addMouseListener(ma);
		inputTables.get(2).addMouseListener(ma);

		// Select all items by clicking Ctrl+A
		KeyAdapter ka = new KeyAdapter(){	
			public void keyPressed(KeyEvent e) {
				if ((( e.stateMask & SWT.CTRL) != 0 ) && ( e.keyCode==97 )) {
					Table t = (Table) e.getSource();
					t.setSelection(t.getItems());
				}
			}
		};
		inputTables.get(0).addKeyListener(ka);
		inputTables.get(1).addKeyListener(ka);
		inputTables.get(2).addKeyListener(ka);
			
		// Options tab
		comp = new Composite(tabFolder, SWT.NONE);
		comp.setLayout(new GridLayout(2, false));
		tiOptions = new TabItem(tabFolder, SWT.NONE);
		tiOptions.setText(Res.getString("OPTTAB_CAPTION")); //$NON-NLS-1$
		tiOptions.setControl(comp);
		
		Group group = new Group(comp, SWT.NONE);
		group.setText(Res.getString("OPTTAB_GRPSOURCE")); //$NON-NLS-1$
		group.setLayoutData(new GridData(GridData.FILL_BOTH));
		group.setLayout(new GridLayout());
		
		Label label = new Label(group, SWT.NONE);
		label.setText(Res.getString("OPTTAB_LANG")); //$NON-NLS-1$
		
		edSourceLang = new Text(group, SWT.BORDER);
		edSourceLang.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		edSourceLang.addModifyListener(new ModifyListener () {
			public void modifyText(ModifyEvent e) {
				updateSourceLanguageSelection();
			}
		});

		lbSourceLang = new List(group, SWT.BORDER | SWT.V_SCROLL);
		gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 2;
		gdTmp.heightHint = 30;
		lbSourceLang.setLayoutData(gdTmp);
		lbSourceLang.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int n = lbSourceLang.getSelectionIndex();
				if ( n > -1 ) {
					inSourceLangSelection = true;
					edSourceLang.setText(lm.getItem(n).code);
					pnlPathBuilder.setSourceLanguage(lm.getItem(n).code);
					inSourceLangSelection = false;
				}
			};
		});

		label = new Label(group, SWT.NONE);
		label.setText(Res.getString("OPTTAB_SRCENC")); //$NON-NLS-1$
		
		edSourceEnc = new Text(group, SWT.BORDER);
		edSourceEnc.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		edSourceEnc.addModifyListener(new ModifyListener () {
			public void modifyText(ModifyEvent e) {
				updateSourceEncodingSelection();
			}
		});
		
		lbSourceEnc = new List(group, SWT.BORDER | SWT.V_SCROLL);
		gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 2;
		gdTmp.heightHint = 30;
		lbSourceEnc.setLayoutData(gdTmp);
		lbSourceEnc.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int n = lbSourceEnc.getSelectionIndex();
				if ( n > -1 ) {
					inSourceEncSelection = true;
					edSourceEnc.setText(em.getItem(n).ianaName);
					inSourceEncSelection = false;
				}
			};
		});

		group = new Group(comp, SWT.NONE);
		group.setText(Res.getString("OPTTAB_GRPTARGET")); //$NON-NLS-1$
		group.setLayoutData(new GridData(GridData.FILL_BOTH));
		group.setLayout(new GridLayout());
		
		label = new Label(group, SWT.NONE);
		label.setText(Res.getString("OPTTAB_LANG")); //$NON-NLS-1$
		
		edTargetLang = new Text(group, SWT.BORDER);
		edTargetLang.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		edTargetLang.addModifyListener(new ModifyListener () {
			public void modifyText(ModifyEvent e) {
				updateTargetLanguageSelection();
			}
		});

		lbTargetLang = new List(group, SWT.BORDER | SWT.V_SCROLL);
		gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 2;
		gdTmp.heightHint = 30;
		lbTargetLang.setLayoutData(gdTmp);
		lbTargetLang.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int n = lbTargetLang.getSelectionIndex();
				if ( n > -1 ) {
					inTargetLangSelection = true;
					edTargetLang.setText(lm.getItem(n).code);
					pnlPathBuilder.setTargetLanguage(lm.getItem(n).code);
					inTargetLangSelection = false;
				}
			};
		});

		label = new Label(group, SWT.NONE);
		label.setText(Res.getString("OPTTAB_TRGENC")); //$NON-NLS-1$
		
		edTargetEnc = new Text(group, SWT.BORDER);
		edTargetEnc.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		edTargetEnc.addModifyListener(new ModifyListener () {
			public void modifyText(ModifyEvent e) {
				updateTargetEncodingSelection();
			}
		});

		lbTargetEnc = new List(group, SWT.BORDER | SWT.V_SCROLL);
		gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 2;
		gdTmp.heightHint = 30;
		lbTargetEnc.setLayoutData(gdTmp);
		lbTargetEnc.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				int n = lbTargetEnc.getSelectionIndex();
				if ( n > -1 ) {
					inTargetEncSelection = true;
					edTargetEnc.setText(em.getItem(n).ianaName);
					inTargetEncSelection = false;
				}
			};
		});
		
		LanguageItem li;
		for ( int i=0; i<lm.getCount(); i++ ) {
			li = lm.getItem(i);
			lbSourceLang.add(li.name + "  -  " + li.code); //$NON-NLS-1$
			lbTargetLang.add(li.name + "  -  " + li.code); //$NON-NLS-1$
		}
		
		EncodingItem ei;
		for ( int i=0; i<em.getCount(); i++ ) {
			ei = em.getItem(i);
			lbSourceEnc.add(ei.name + "  -  " + ei.ianaName); //$NON-NLS-1$
			lbTargetEnc.add(ei.name + "  -  " + ei.ianaName); //$NON-NLS-1$
		}
		
		// Other settings tab
		comp = new Composite(tabFolder, SWT.NONE);
		comp.setLayout(new GridLayout());
		tiOptions = new TabItem(tabFolder, SWT.NONE);
		tiOptions.setText(Res.getString("MainForm.tabOtherSettings")); //$NON-NLS-1$
		tiOptions.setControl(comp);
		
		group = new Group(comp, SWT.NONE);
		group.setLayout(new GridLayout(2, false));
		group.setText(Res.getString("MainForm.outputGroup")); //$NON-NLS-1$
		group.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		chkUseOutputRoot = new Button(group, SWT.CHECK);
		chkUseOutputRoot.setText(Res.getString("MainForm.outputUseRoot")); //$NON-NLS-1$
		chkUseOutputRoot.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				edOutputRoot.setEnabled(chkUseOutputRoot.getSelection());
				updateOutputRoot();
			};
		});
		
		edOutputRoot = new Text(group, SWT.SINGLE | SWT.BORDER);
		edOutputRoot.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		edOutputRoot.addModifyListener(new ModifyListener () {
			public void modifyText(ModifyEvent e) {
				updateOutputRoot();
			}
		});
		
		pnlPathBuilder = new PathBuilderPanel(group, SWT.NONE);
		gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 2;
		pnlPathBuilder.setLayoutData(gdTmp);

		group = new Group(comp, SWT.NONE);
		group.setLayout(new GridLayout(3, false));
		group.setText(Res.getString("MainForm.paramFolderGroup")); //$NON-NLS-1$
		group.setLayoutData(new GridData(GridData.FILL_BOTH));

		label = new Label(group, SWT.NONE);
		label.setText(Res.getString("MainForm.paramFolder")); //$NON-NLS-1$
		
		edParamsFolder = new Text(group, SWT.BORDER);
		edParamsFolder.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		btGetParamsFolder = new Button(group, SWT.PUSH);
		btGetParamsFolder.setText("..."); //$NON-NLS-1$
		btGetParamsFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				browseParamsFolder();
            }
		});
		
		// Space-holder
		new Label(group, SWT.NONE);
		
		chkUseCustomParametersFolder = new Button(group, SWT.CHECK);
		chkUseCustomParametersFolder.setText(Res.getString("MainForm.useCustomParamFolder")); //$NON-NLS-1$
		chkUseCustomParametersFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				// First save the custom folder if it was custom (so !was)
				if ( !chkUseCustomParametersFolder.getSelection() ) {
					prj.setCustomParametersFolder(edParamsFolder.getText());
				}
				btGetParamsFolder.setEnabled(chkUseCustomParametersFolder.getSelection());
				edParamsFolder.setEditable(chkUseCustomParametersFolder.getSelection());
				edParamsFolder.setText(prj.getParametersFolder(chkUseCustomParametersFolder.getSelection(), true));
				customFilterConfigsNeedUpdate = true;
            }
		});
	
		
		// Tabs change event (define here to avoid triggering it while creating the content)
		tabFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				updateTabInfo();
            }
		});

		statusBar = new StatusBar(shell, SWT.NONE);
		updateMRU();
		
		if (!shell.getMaximized()) { // not RWT full-screen mode
			// Set the minimal size to the packed size
			// And then set the start size
			Point startSize = shell.getSize();
			shell.pack();
			shell.setMinimumSize(shell.getSize());
			shell.setSize(startSize);
			
			// Workaround for RWT (stretches the shell horizontally when column widths are adjusted)
			shell.setMaximized(true);
			shell.setMaximized(false);

			UIUtil.centerShell(shell);
			
			// Maximize if requested
			if ( config.getBoolean("maximized") ) { //$NON-NLS-1$
				shell.setMaximized(true);
			}
			else { // Or try to re-use the bounds of the previous session
				Rectangle ar = UIUtil.StringToRectangle(config.getProperty(OPT_BOUNDS));
				if ( ar != null ) {
					Rectangle dr = shell.getDisplay().getBounds();
					if ( dr.contains(ar.x+ar.width, ar.y+ar.height)
						&& dr.contains(ar.x, ar.y) ) {
						shell.setBounds(ar);
					}
				}
			}
		}		
	}

	private void updatePluginsAndDependencies () {
		// Re-discover the plugins
		pm.discover(new File(getDropinsDirectory()), true);
		
		// Update the filters
		fcMapper.addFromPlugins(pm); //TODO: Need to remove as well!!!
		customFilterConfigsNeedUpdate = true;
		fm.addConfigurations(fcMapper);
		
		// Update connectors
		DefaultConnectors.addFromPlugins(pm);
		
		// Update the steps
		if ( wrapper != null ) {
			wrapper.refreshAvailableStepsList();
		}
	}

	private void setLogLevel () {
		int n = config.getInteger(MainForm.OPT_LOGLEVEL);
		switch ( n ) {
		case 1: // Debug
			logHandler.setLogLevel(ILogHandler.LogLevel.DEBUG);
			break;
		case 2: // Trace
			logHandler.setLogLevel(ILogHandler.LogLevel.TRACE);
			break;
		default:
			logHandler.setLogLevel(ILogHandler.LogLevel.INFO);
			break;
		}
	}

	private void createToolbar () {
		ToolBar toolbar = new ToolBar(shell, SWT.FLAT | SWT.WRAP);
		GridData gdTmp = new GridData(GridData.FILL_HORIZONTAL);
		gdTmp.horizontalSpan = 3;
		toolbar.setLayoutData(gdTmp);
		
		ToolItem item = new ToolItem(toolbar, SWT.PUSH);
	    item.setImage(rm.getImage("newproject")); //$NON-NLS-1$
	    item.setToolTipText(Res.getString("MainForm.newProjectTip")); //$NON-NLS-1$
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				createProject(true);
			}
		});
	    
		item = new ToolItem(toolbar, SWT.PUSH);
	    item.setImage(rm.getImage("openproject")); //$NON-NLS-1$
	    item.setToolTipText(Res.getString("MainForm.openProjectTip")); //$NON-NLS-1$
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				openProject(null);
			}
		});
	    
		item = new ToolItem(toolbar, SWT.PUSH);
	    item.setImage(rm.getImage("saveproject")); //$NON-NLS-1$
	    item.setToolTipText(Res.getString("MainForm.saveProjectTip")); //$NON-NLS-1$
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				saveProject(prj.path);
			}
		});
		
		new ToolItem(toolbar, SWT.SEPARATOR);

		item = new ToolItem(toolbar, SWT.PUSH);
	    item.setImage(rm.getImage("log")); //$NON-NLS-1$
	    item.setToolTipText(Res.getString("MainForm.logTip")); //$NON-NLS-1$
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if ( log.isVisible() ) log.hide();
				else log.show();
			}
		});

		new ToolItem(toolbar, SWT.SEPARATOR);

		tbiAddDocs = new ToolItem(toolbar, SWT.PUSH);
		tbiAddDocs.setImage(rm.getImage("addinput")); //$NON-NLS-1$
		tbiAddDocs.setToolTipText(Res.getString("MainForm.addDocsTip")); //$NON-NLS-1$
		tbiAddDocs.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				addDocumentsFromList(null);
			}
		});

		tbiOpenFolder = new ToolItem(toolbar, SWT.PUSH);
		tbiOpenFolder.setImage(rm.getImage("openfolder")); //$NON-NLS-1$
		tbiOpenFolder.setToolTipText(Res.getString("MainForm.openContFolderTip")); //$NON-NLS-1$
		tbiOpenFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				openContainingFolder(-1);
			}
		});

		tbiMoveUp = new ToolItem(toolbar, SWT.PUSH);
		tbiMoveUp.setImage(rm.getImage("moveup")); //$NON-NLS-1$
		tbiMoveUp.setToolTipText(Res.getString("MainForm.moveUpTip")); //$NON-NLS-1$
		tbiMoveUp.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				moveDocumentsUp();
			}
		});

		tbiMoveDown = new ToolItem(toolbar, SWT.PUSH);
		tbiMoveDown.setImage(rm.getImage("movedown")); //$NON-NLS-1$
		tbiMoveDown.setToolTipText(Res.getString("MainForm.moveDownTip")); //$NON-NLS-1$
		tbiMoveDown.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				moveDocumentsDown();
			}
		});

		tbiEditDocProp = new ToolItem(toolbar, SWT.PUSH);
		tbiEditDocProp.setImage(rm.getImage("properties")); //$NON-NLS-1$
		tbiEditDocProp.setToolTipText(Res.getString("MainForm.editInputPropTip")); //$NON-NLS-1$
		tbiEditDocProp.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				editInputProperties(-1);
			}
		});

		new ToolItem(toolbar, SWT.SEPARATOR);

		item = new ToolItem(toolbar, SWT.PUSH);
	    item.setImage(rm.getImage("help")); //$NON-NLS-1$
	    item.setToolTipText(Res.getString("MainForm.helpTip")); //$NON-NLS-1$
		item.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if ( help != null ) help.showWiki("Rainbow");
			}
		});
	}
	
	private void browseParamsFolder () {
		try {
			DirectoryDialog dlg = new DirectoryDialog(shell);
			dlg.setFilterPath(edParamsFolder.getText());
			String dir = dlg.open();
			if (  dir == null ) return;
			edParamsFolder.setText(dir);
			edParamsFolder.selectAll();
			edParamsFolder.setFocus();
			customFilterConfigsNeedUpdate = true;
		}
		catch ( Throwable e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}

	/**
	 * Executes (and edit) a pipeline.
	 * @param predefinedPipeline optional predefined pipeline or null.
	 */
	private void executePipeline (IPredefinedPipeline predefinedPipeline) {
		try {
			setupPipelineWrapper();
			if ( predefinedPipeline == null ) {
				wrapper.loadFromStringStorageOrReset(prj.getUtilityParameters(PRJPIPELINEID));
			}
			else {
				// If we have a predefined pipeline: set it
				// Get the parameters data from the project
				predefinedPipeline.setParameters(wrapper.getAvailableSteps(),
					prj.getUtilityParameters(predefinedPipeline.getId()));
				// Load the pipeline
				wrapper.loadPipeline(predefinedPipeline, null);
			}

			PipelineEditor dlg = new PipelineEditor();
			int res = dlg.edit(shell, wrapper.getAvailableSteps(), wrapper,
				(predefinedPipeline==null) ? null : predefinedPipeline.getTitle(),
				help, null,
				(predefinedPipeline==null) ? -1 : predefinedPipeline.getInitialStepIndex());
			
			if ( res == PipelineEditor.RESULT_CANCEL ) {
				return; // No execution, no save
			}

			
			// Save the pipeline info as default pipeline for the project
			if ( predefinedPipeline == null ) {
				prj.setUtilityParameters(PRJPIPELINEID,
					wrapper.getStringStorage());
			}
			else { // If it's a predefined pipeline: save the parameters
				wrapper.copyParametersToPipeline(predefinedPipeline);
				prj.setUtilityParameters(predefinedPipeline.getId(),
					predefinedPipeline.getParameters());
			}
			
			if ( res == PipelineEditor.RESULT_CLOSE ) {
				return; // No execution
			}

			// Else: execute
			startWaiting(Res.getString("MainForm.startWaiting"), true); //$NON-NLS-1$
			wrapper.execute(prj);
		}
		catch ( Throwable e ) {
			log.error(e.getMessage());
		}
		finally {
			stopWaiting();
			if ( log.getErrorAndWarningCount() > 0 ) log.show();
		}
	}

	private void updateCustomConfigurations () {
		if ( customFilterConfigsNeedUpdate ) {
			fcMapper.setCustomConfigurationsDirectory(prj.getParametersFolder());
			fcMapper.updateCustomConfigurations();
			customFilterConfigsNeedUpdate = false;
		}
	}
	
	private void updateTabInfo () {
		if ( tabFolder.getSelectionIndex() < inputTables.size() ) {
			currentInput = tabFolder.getSelectionIndex();
		}
		else {
			currentInput = -1;
		}
		updateCommands();
		updateInputRoot();
		
		miInput.setEnabled(currentInput!=-1);
		btGetRoot.setEnabled(currentInput!=-1);
		
		tbiAddDocs.setEnabled(currentInput!=-1);
		tbiMoveUp.setEnabled(currentInput!=-1);
		tbiMoveDown.setEnabled(currentInput!=-1);
		tbiOpenFolder.setEnabled(currentInput!=-1);
		tbiEditDocProp.setEnabled(currentInput!=-1);
	}
	
	private void buildInputTab (int index,
		final Composite comp)
	{
		final Table table = new Table(comp, SWT.BORDER | SWT.MULTI | SWT.FULL_SELECTION);
		
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		GridData gdTmp = new GridData(GridData.FILL_BOTH);
		gdTmp.horizontalSpan = 1;
		table.setLayoutData(gdTmp);
		table.addControlListener(new ControlAdapter() {
		    public void controlResized(ControlEvent e) {
		    	Table table = (Table)e.getSource();
		    	Rectangle rect = table.getClientArea();
				//TODO: Check behavior when manual resize a column width out of client area
				int nPart = (int)(rect.width / 100);
				table.getColumn(0).setWidth(70*nPart);
				table.getColumn(1).setWidth(rect.width-table.getColumn(0).getWidth());
		    }
		});
		table.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if ( e.character == ' ' ) {
					editInputProperties(-1);
				}
				else if ( e.keyCode == SWT.DEL ) {
					removeDocuments(-1);
				}
			}
		});
		table.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				statusBar.setCounter(table.getSelectionIndex(),
					table.getItemCount());
            }
		});

		InputTableModel model = new InputTableModel();
		model.linkTable(table);

		// Drop target for the table
		DropTarget dropTarget = new DropTarget(table, DND.DROP_DEFAULT | DND.DROP_COPY | DND.DROP_MOVE);
		dropTarget.setTransfer(new FileTransfer[]{FileTransfer.getInstance()}); 
		dropTarget.addDropListener(new DropTargetAdapter() {
			public void drop (DropTargetEvent e) {
				FileTransfer FT = FileTransfer.getInstance();
				if ( FT.isSupportedType(e.currentDataType) ) {
					String[] paths = (String[])e.data;
					if ( paths != null ) {
						addDocumentsFromList(paths);
					}
				}
			}
		});
		
		inputTables.add(table);
		inputTableMods.add(model);
		currentInput = index;
	}
	
	private void buildUtilitiesMenu () {
		// Remove an existing menu
		Menu menu = miUtilities.getMenu();
		if ( menu != null ) menu.dispose();
		// Create new one
		Menu dropMenu = new Menu(shell, SWT.DROP_DOWN);
		miUtilities.setMenu(dropMenu);
		
		// Add the default entries
		miOpenOutputFolder = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(miOpenOutputFolder, "utilities.openOutputFolder"); //$NON-NLS-1$
		miOpenOutputFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if ( prj.getLastOutputFolder() == null ) return;
				Program.launch(prj.getLastOutputFolder());
            }
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		MenuItem menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.pipeline"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(null);
			}
		});
		
		// Add pre-defined pipelines

		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.translationkitcreation"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new TranslationKitCreationPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.translationkitpostprocessing"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new TranslationKitPostProcessingPipeline());
			}
		});
		
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.snrwithfilter"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new SnRWithFilterPipeline());
			}
		});

		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.snrwithoutfilter"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new SnRWithoutFilterPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.textrewriting"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new TextRewritingPipeline());
			}
		});

		//--- Sub menus
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		//--- Conversion Utilities
		
		final MenuItem conversionUtilitiesMenu = new MenuItem(dropMenu, SWT.CASCADE);
		conversionUtilitiesMenu.setText("Conversion Utilities");
		Menu subMenu = new Menu(shell, SWT.DROP_DOWN);
		conversionUtilitiesMenu.setMenu(subMenu);
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.rtfconversion"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new RTFConversionPipeline());
			}
		});
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.formatconversion"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new FormatConversionPipeline());
			}
		});
		
		new MenuItem(subMenu, SWT.SEPARATOR);

		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.encodingconversion"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new EncodingConversionPipeline());
			}
		});
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.linebreakconversion"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new LineBreakConversionPipeline());
			}
		});
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.bomconversion"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new BOMConversionPipeline());
			}
		});

		new MenuItem(subMenu, SWT.SEPARATOR);

		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.uriconversion"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new URIConversionPipeline());
			}
		});
		
		
		//--- XML / XLIFF Utilities
		
		final MenuItem xmlUtilitiesMenu = new MenuItem(dropMenu, SWT.CASCADE);
		xmlUtilitiesMenu.setText("XML Utilities");
		subMenu = new Menu(shell, SWT.DROP_DOWN);
		xmlUtilitiesMenu.setMenu(subMenu);
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.xmlanalysis"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new XMLAnalysisPipeline());
			}
		});
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.xmlvalidation"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new XMLValidationPipeline());
			}
		});
		
		new MenuItem(subMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.xmlcharsfixing"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new XMLCharactersFixingPipeline());
			}
		});
		
		menuItem = new MenuItem(subMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.xsltransform"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new XSLTransformPipeline());
			}
		});
		
		//--- Other
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.imagemodification"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new ImageModificationPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.termextraction"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new TermExtractionPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.importtm"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new ImportTMPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.batchtranslation"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new BatchTranslationPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.qualitycheck"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new QualityCheckPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.transcomparison"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new TranslationComparisonPipeline());
			}
		});
		
		menuItem = new MenuItem(dropMenu, SWT.PUSH);
		rm.setCommand(menuItem, "utilities.charlisting"); //$NON-NLS-1$
		menuItem.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				executePipeline(new CharListingPipeline());
			}
		});

		// Add the plug-in utilities
		new MenuItem(dropMenu, SWT.SEPARATOR);
		
		Iterator<String> iter = utilitiesAccess.getIterator();
		while ( iter.hasNext() ) {
			UtilitiesAccessItem item = utilitiesAccess.getItem(iter.next());
			if ( item.type == -1 ) {
				new MenuItem(dropMenu, SWT.SEPARATOR);
			}
			else {
				menuItem = new MenuItem(dropMenu, SWT.PUSH);
				menuItem.setText(item.name+"..."); //$NON-NLS-1$
				menuItem.setData(item.id);
				menuItem.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent event) {
						launchUtility((String)((MenuItem)event.getSource()).getData());
					}
				});
			}
		}
		
	}

	public void run () {
		try {
			Display Disp = shell.getDisplay();
			while ( !shell.isDisposed() ) {
				if (!Disp.readAndDispatch())
					Disp.sleep();
			}
		}
		finally {
			// Dispose of any global resources
			if ( rm != null ) rm.dispose();
		}
	}

	private void launchUtility (String utilityID) {
		try {
			if ( utilityID == null ) return;
			// Save any pending data
			saveSurfaceData();
			updateCustomConfigurations();
			// Create the utility driver if needed
			if ( ud == null ) {
				ud = new UtilityDriver(log, fcMapper, utilitiesAccess, help, true);
			}
			// Get the data for the utility and instantiate it
			ud.setData(prj, utilityID);
			// Run it
			if ( !ud.checkParameters(shell) ) return;
			startWaiting(Res.getString("MainForm.startWaiting"), true); //$NON-NLS-1$
			ud.execute(shell);
			// Gets the latest folder to open.
			prj.setLastOutpoutFolder(ud.getUtility().getFolderAfterProcess());
		}
		catch ( Exception E ) {
			Dialogs.showError(shell, E.getMessage(), null);
		}
		finally {
			miOpenOutputFolder.setEnabled(prj.getLastOutputFolder()!=null);
			stopWaiting();
		}
	}

	private void updateSourceLanguageSelection () {
		if ( inSourceLangSelection ) return;
		int n = lm.getIndexFromCode(edSourceLang.getText());
		if ( n > -1 ) {
			lbSourceLang.setSelection(n);
			lbSourceLang.showSelection();
		}
		pnlPathBuilder.setSourceLanguage(edSourceLang.getText());
	}

	private void updateTargetLanguageSelection () {
		if ( inTargetLangSelection ) return;
		int n = lm.getIndexFromCode(edTargetLang.getText());
		if ( n > -1 ) {
			lbTargetLang.setSelection(n);
			lbTargetLang.showSelection();
		}
		pnlPathBuilder.setTargetLanguage(edTargetLang.getText());
	}

	private void updateSourceEncodingSelection () {
		if ( inSourceEncSelection ) return;
		int n = em.getIndexFromIANAName(edSourceEnc.getText());
		if ( n > -1 ) {
			lbSourceEnc.setSelection(n);
			lbSourceEnc.showSelection();
		}
	}

	private void updateTargetEncodingSelection () {
		if ( inTargetEncSelection ) return;
		int n = em.getIndexFromIANAName(edTargetEnc.getText());
		if ( n > -1 ) {
			lbTargetEnc.setSelection(n);
			lbTargetEnc.showSelection();
		}
	}

	private void updateOutputRoot () {
		try {
			if ( chkUseOutputRoot.getSelection() ) {
				pnlPathBuilder.setTargetRoot(edOutputRoot.getText());
			}
			else {
				pnlPathBuilder.setTargetRoot(null);
			}
			pnlPathBuilder.updateSample();
		}
		catch ( Exception E ) {
			Dialogs.showError(shell, E.getMessage(), null);
		}
	}
	
	private void setDirectories () throws UnsupportedEncodingException {
    	// Get the location of the main class source
    	File file = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getFile());
    	appRootFolder = URLDecoder.decode(file.getAbsolutePath(),"utf-8"); //$NON-NLS-1$
    	// Remove the JAR file if running an installed version
    	boolean fromJar = appRootFolder.endsWith(".jar"); //$NON-NLS-1$
    	if ( fromJar ) appRootFolder = Util.getDirectoryName(appRootFolder);
    	// Remove the application folder in all cases
    	appRootFolder = Util.getDirectoryName(appRootFolder);
		sharedFolder = Utils.getOkapiSharedFolder(appRootFolder, fromJar);
		help = new BaseHelp(appRootFolder+File.separator+"help"); //$NON-NLS-1$
	}
	
	private void startWaiting (String text,
		boolean p_bStartLog)
	{
		if ( ++waitCount > 1 ) {
			shell.getDisplay().update();
			return;
		}
		if ( text != null ) statusBar.setInfo(text);
		startLogWasRequested = p_bStartLog;
		if ( startLogWasRequested ) {
			log.beginProcess(null);
			if ( config.getBoolean(OPT_ALWAYSOPENLOG) ) {
				log.show();
			}
		}
		shell.getDisplay().update();
	}

	private void stopWaiting () {
		waitCount--;
		if ( waitCount < 1 ) statusBar.clearInfo();
		shell.getDisplay().update();
		if ( log.inProgress() ) log.endProcess(null); 
		if ( startLogWasRequested && ( log.getErrorAndWarningCount() > 0 )) log.show();
		startLogWasRequested = false;
	}

	private void updateCommands () {
		boolean enabled = (( currentInput > -1 ) && ( currentInput < inputTables.size() ));
		if ( enabled ) {
			int total = inputTables.get(currentInput).getItemCount();
			enabled = ( total > 0);
			statusBar.setCounter(inputTables.get(currentInput).getSelectionIndex(), total);
		}
		else {
			statusBar.setCounter(-1, 0);
		}

		miEditInputProperties.setEnabled(enabled);
		cmiEditInputProperties.setEnabled(enabled);
		miOpenInputDocument.setEnabled(enabled);
		cmiOpenInputDocument.setEnabled(enabled);
		miRemoveInputDocuments.setEnabled(enabled);
		cmiRemoveInputDocuments.setEnabled(enabled);

		miOpenFolder.setEnabled(enabled);
		cmiOpenFolder.setEnabled(enabled);
		miOpenOutputFolder.setEnabled(prj.getLastOutputFolder()!=null);
		
		miMoveDocumentsUp.setEnabled(enabled);
		cmiMoveDocumentsUp.setEnabled(enabled);
		miMoveDocumentsDown.setEnabled(enabled);
		cmiMoveDocumentsDown.setEnabled(enabled);
	}
	
	private void loadResources ()
		throws Exception 
	{
		rm = new ResourceManager(MainForm.class, shell.getDisplay());
		rm.addImages("rainbow", "rainbow16", "rainbow32"); //$NON-NLS-1$
		rm.addImage("newproject"); //$NON-NLS-1$
		rm.addImage("openproject"); //$NON-NLS-1$
		rm.addImage("saveproject"); //$NON-NLS-1$
		rm.addImage("log"); //$NON-NLS-1$
		rm.addImage("addinput"); //$NON-NLS-1$
		rm.addImage("openfolder"); //$NON-NLS-1$
		rm.addImage("properties"); //$NON-NLS-1$
		rm.addImage("moveup"); //$NON-NLS-1$
		rm.addImage("movedown"); //$NON-NLS-1$
		rm.addImage("help"); //$NON-NLS-1$
		
		//TODO: deal with commands localization
		rm.loadCommands("net.sf.okapi.applications.rainbow.Commands"); //$NON-NLS-1$

		fm = new FormatManager();
		fm.load(null); // TODO: implement real external file, for now it's hard-coded
		lm = new LanguageManager();
		lm.loadList(sharedFolder + File.separator + "languages.xml"); //$NON-NLS-1$
		em = new EncodingManager();
		em.loadList(sharedFolder + File.separator + "encodings.xml"); //$NON-NLS-1$
		utilitiesAccess = new UtilitiesAccess();
		utilitiesAccess.loadMenu(sharedFolder + File.separator + "rainbowUtilities.xml"); //$NON-NLS-1$
	}
	
	private void editUserPreferences () {
		try {
			saveSurfaceData();
			PreferencesForm dlg = new PreferencesForm(shell, help);
			dlg.setData(config);
			dlg.showDialog();
			
			// Update dependent data
			setLogLevel();
			updatePluginsAndDependencies();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void changeRoot () {
		try {
			saveSurfaceData();
			InputDialog dlg = new InputDialog(shell,
				String.format(Res.getString("MainForm.rootCaption"), currentInput+1), //$NON-NLS-1$
				Res.getString("MainForm.editRootLabel"), //$NON-NLS-1$
				prj.getRawInputRoot(currentInput), null, 1, -1, -1);
			dlg.setAllowEmptyValue(true);
			String newRoot = dlg.showDialog();
			if ( newRoot == null ) return; // Canceled
			if ( newRoot.length() < 2 ) newRoot = ""; // Use project's //$NON-NLS-1$
			
			//--remove trailing separator
			if (newRoot.endsWith(File.separator)){
				newRoot = newRoot.substring(0, newRoot.length() - 1);
			}
			
			//--check expanded dir exists
			File chkFile = new File(Util.expandPath(newRoot, prj.getProjectFolder(), null));
			if (! chkFile.isDirectory() && newRoot.length() > 0)
				throw new OkapiException("Not a valid directory");
			
			prj.setInputRoot(currentInput, newRoot, newRoot.length()>0);
			resetDisplay(currentInput);
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}

	/**
	 * Gets the table index if the input currently focused. If none is
	 * focused, tries to selected the first item. 
	 * @return The index of the focused item, or -1.
	 */
	private int getFocusedInputIndex () {
		int index = inputTables.get(currentInput).getSelectionIndex();
		if ( index == -1 ) {
			if ( inputTables.get(currentInput).getItemCount() > 0 ) {
				inputTables.get(currentInput).select(0);
				return inputTables.get(currentInput).getSelectionIndex();
			}
			else return -1;
		}
		return inputTables.get(currentInput).getSelectionIndex();
	}
	
	private void saveUserConfiguration () {
		// Set the window placement
		config.setProperty("maximized", shell.getMaximized()); //$NON-NLS-1$
		Rectangle r = shell.getBounds();
		config.setProperty(OPT_BOUNDS, String.format("%d,%d,%d,%d", r.x, r.y, r.width, r.height)); //$NON-NLS-1$
		// Set the MRU list
		mruList.copyToProperties(config);
		// Source and target locales and encodings
		if ( config.getBoolean(OPT_USEUSERDEFAULTS) ) saveSurfaceData();
		config.setProperty(OPT_SOURCELOCALE, prj.getSourceLanguage().toString());
		config.setProperty(OPT_SOURCEENCODING, prj.getSourceEncoding());
		config.setProperty(OPT_TARGETLOCALE, prj.getTargetLanguage().toString());
		config.setProperty(OPT_TARGETENCODING, prj.getTargetEncoding());

		// Save to the user home directory as ".appname" file
		config.save(APPNAME, getClass().getPackage().getImplementationVersion());
	}

	private boolean canContinue (boolean falseOnError) {
		try {
			saveSurfaceData();
			if ( !prj.isModified ) return true;
			else {
				// Ask confirmation
				MessageBox dlg = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
				dlg.setMessage(Res.getString("MainForm.askToSave")); //$NON-NLS-1$
				dlg.setText(APPNAME);
				switch  ( dlg.open() ) {
				case SWT.NO:
					return true;
				case SWT.CANCEL:
					return false;
				}
				// Else save the project
				saveProject(prj.path);
			}
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
			if ( falseOnError ) return false;
		}
		return true;
	}
	
	private void clearMRU () {
		try {
			mruList.clear();
			updateMRU();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void updateMRU () {
		try {
			// miMRU is the MenuItem where to attached the sub-menu
			// Remove and dispose of the previous sub-menu
			Menu oldMenu = miMRU.getMenu();
			miMRU.setMenu(null);
			if ( oldMenu != null ) oldMenu.dispose();

			// Set the new one
			if ( mruList.getfirst() == null ) {
				// No items to set: it's disabled
				miMRU.setEnabled(false);
			}
			else { // One or more items
				// Create the menu
				Menu submenu = new Menu(shell, SWT.DROP_DOWN);
				int i = 0;
				String path;
				MenuItem menuItem;
				Iterator<String> iter = mruList.getIterator();
				while ( iter.hasNext() ) {
					menuItem = new MenuItem(submenu, SWT.PUSH);
					path = iter.next();
					menuItem.setText(String.format("&%d %s", ++i, path)); //$NON-NLS-1$
					menuItem.setData(path);
					menuItem.addSelectionListener(new SelectionAdapter() {
						public void widgetSelected(SelectionEvent event) {
							openProject((String)((MenuItem)event.getSource()).getData());
						}
					});
				}
				miMRU.setMenu(submenu);
				miMRU.setEnabled(true);
			}
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void saveProject (String path) {
		try {
			if ( path == null ) {
				path = Dialogs.browseFilenamesForSave(shell, Res.getString("MainForm.saveProjectBrowseCaption"), null, null, //$NON-NLS-1$
					Res.getString("MainForm.33"), "*.rnb");  //$NON-NLS-1$//$NON-NLS-2$
				if ( path == null ) return;
				mruList.add(path);
				updateMRU();
			}
			saveSurfaceData();
			prj.save(path);
			updateTitle();
			edParamsFolder.setText(prj.getParametersFolder(true));
			updateInputRoot();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}

	private void updateTitle () {
		shell.setText(((prj.path == null)
			? Res.getString("UNTITLED") //$NON-NLS-1$
			: Util.getFilename(prj.path, true))
			+ " - " //$NON-NLS-1$ 
			+ APPNAME);
	}

	// Use -1 for all lists
	private void resetDisplay (int listIndex) {
		updateTitle();

		if (( listIndex < 0 ) || ( listIndex == 0 ))
			inputTableMods.get(0).setProject(prj.getList(0));
		if (( listIndex < 0 ) || ( listIndex == 1 ))
			inputTableMods.get(1).setProject(prj.getList(1));
		if (( listIndex < 0 ) || ( listIndex == 2 ))
			inputTableMods.get(2).setProject(prj.getList(2));
		
		setSurfaceData();
		updateCommands();
		if ( currentInput != -1 ) {
			inputTables.get(currentInput).setFocus();
		}
	}
	
	private void createProject (boolean checkCanContinue ) {
		if ( checkCanContinue ) {
			if ( !canContinue(true) ) return;
		}
		
		prj = new Project(lm);

		// Overwrite locale/encoding with user defaults if needed
		if ( config.getBoolean(OPT_USEUSERDEFAULTS) ) {
			String tmp = config.getProperty(OPT_SOURCELOCALE);
			if ( !Util.isEmpty(tmp) ) prj.setSourceLanguage(LocaleId.fromString(tmp));
			tmp = config.getProperty(OPT_SOURCEENCODING);
			if ( !Util.isEmpty(tmp) ) prj.setSourceEncoding(tmp);
			tmp = config.getProperty(OPT_TARGETLOCALE);
			if ( !Util.isEmpty(tmp) ) prj.setTargetLanguage(LocaleId.fromString(tmp));
			tmp = config.getProperty(OPT_TARGETENCODING);
			if ( !Util.isEmpty(tmp) ) prj.setTargetEncoding(tmp);
			prj.isModified = false; // User defaults are not modifications
		}
		
		// Set custom parameters folder
		String tmp = config.getProperty(OPT_PARAMSDIR);
		if ( !Util.isEmpty(tmp) ){
			prj.setCustomParametersFolder(tmp);
			prj.setUseCustomParametersFolder(true);
			prj.isModified = false; // User defaults are not modifications
		}
		
		customFilterConfigsNeedUpdate = true;
		wrapper = null;
		currentInput = 0;
		resetDisplay(-1);
	}
	
	private void openProject (String path) {
		try {
			if ( !canContinue(true) ) return;
			if ( path == null ) {
				String[] paths = Dialogs.browseFilenames(shell, Res.getString("MainForm.openProjectBrowsecaption"), false, null, //$NON-NLS-1$
					Res.getString("MainForm.35"), "*.rnb\t*.*");  //$NON-NLS-1$//$NON-NLS-2$
				if ( paths == null ) return;
				path = paths[0];
			}
			
			// Check if the file exists
			if ( !(new File(path)).exists() ) {
				Dialogs.showError(shell, Res.getString("MainForm.projectNotFound")+path, null); //$NON-NLS-1$
				mruList.remove(path);
				updateMRU();
				return;
			}

			// Load it and update the UI
			prj = new Project(lm);
			prj.load(path);
			customFilterConfigsNeedUpdate = true;

			mruList.add(path);
			updateMRU();
			resetDisplay(-1);
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}

	private void setSurfaceData () {
		for ( InputTableModel model : inputTableMods ) {
			model.updateTable(null, 0);
		}

		updatePathBuilderSampleData();
		chkUseOutputRoot.setSelection(prj.getUseOutputRoot());
		edOutputRoot.setText(prj.getOutputRoot());

		edSourceLang.setText(prj.getSourceLanguage().toString());
		edSourceEnc.setText(prj.getSourceEncoding());
		edTargetLang.setText(prj.getTargetLanguage().toString());
		edTargetEnc.setText(prj.getTargetEncoding());
		
		chkUseCustomParametersFolder.setSelection(prj.useCustomParametersFolder());
		btGetParamsFolder.setEnabled(chkUseCustomParametersFolder.getSelection());
		edParamsFolder.setEditable(prj.useCustomParametersFolder());
		edParamsFolder.setText(prj.getParametersFolder(true));
		
		// Updates
		edOutputRoot.setEnabled(chkUseOutputRoot.getSelection());
		updateInputRoot();
		updateOutputRoot();
	}
	
	private void updateInputRoot () {
		if ( currentInput == -1 ) return;
		stInputRoot.setText(String.format(Res.getString("MainForm.inputRootLabel"), currentInput+1)); //$NON-NLS-1$
		edInputRoot.setText(prj.getInputRootDisplay(currentInput));
		updateOutputRoot();
	}

	/*
	 * Updates the data used to show the sample path in the path builder panel.
	 */
	private void updatePathBuilderSampleData () {
		String sampleInput;
		if ( inputTables.get(0).getItemCount() > 0 ) {
			// Use the first file of the first list as example
			sampleInput = prj.getInputRoot(0) + File.separator + inputTables.get(0).getItem(0).getText();
		}
		else {
			sampleInput = prj.getInputRoot(0) + File.separator
				+ Res.getString("MainForm.37") + File.separator //$NON-NLS-1$
				+ Res.getString("MainForm.38"); //$NON-NLS-1$
		}
		pnlPathBuilder.setData(prj.pathBuilder, prj.getInputRoot(0), sampleInput,
			prj.getOutputRoot(), prj.getSourceLanguage().toString(), prj.getTargetLanguage().toString());
	}
	
	/**
	 * Saves the UI-accessible properties of the project into the project object.
	 */
	private void saveSurfaceData () {
		//TODO: Fix this, tmp is already equal because of example display
		String tmp = prj.pathBuilder.toString();
		pnlPathBuilder.saveData(prj.pathBuilder);
		if ( !tmp.equals(prj.pathBuilder.toString()))
			prj.isModified = true;
	
		prj.setUseOutputRoot(chkUseOutputRoot.getSelection());
		prj.setOutputRoot(edOutputRoot.getText());
		prj.setSourceLanguage(LocaleId.fromString(edSourceLang.getText()));
		prj.setSourceEncoding(edSourceEnc.getText());
		prj.setTargetLanguage(LocaleId.fromString(edTargetLang.getText()));
		prj.setTargetEncoding(edTargetEnc.getText());
		
		prj.setUseCustomParametersFolder(chkUseCustomParametersFolder.getSelection());
		if ( prj.useCustomParametersFolder() ) {
			prj.setCustomParametersFolder(edParamsFolder.getText());
		}
	}

	private void addDocumentsFromList (String[] paths) {
		try {
			if ( currentInput == -1 ) return;
			saveSurfaceData();
			// Get a list of paths if needed
			if ( paths == null ) {
				paths = Dialogs.browseFilenames(shell, Res.getString("MainForm.addDocsBrowsecaption"), //$NON-NLS-1$
					true, prj.getInputRoot(currentInput), null, null);
			}
			if ( paths == null ) return;
			// Add all the selected files and folders
			startWaiting(Res.getString("MainForm.addingInputDocs"), false); //$NON-NLS-1$
			doAddDocuments(paths, null);
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			inputTableMods.get(currentInput).updateTable(null, 0);
			updatePathBuilderSampleData();
			updateCommands();
			stopWaiting();
		}
	}

	private int doAddDocuments (String[] paths,
		String dir)
		throws Exception
	{
		boolean resetDisp = false;
		int n = 0;
		boolean allowDup = config.getBoolean(OPT_ALLOWDUPINPUT);
		for ( String path : paths ) {
			if ( dir != null ) {
				path = dir + File.separator + path;
			}
			File f = new File(path);
			// Prevent expand of directories in some cases
			boolean allowExpand = (Util.isEmpty(Util.getExtension(path)) || (NOEXPAND_EXTENSIONS.indexOf(Util.getExtension(path))==-1));
			
			// Expand (for directories) or add (for files)
			if ( allowExpand && f.isDirectory() ) {
				n += doAddDocuments(f.list(), path);
			}
			else {
				String[] res = fm.guessFormat(path);
				
				// If project is not saved and it's the first added file:
				if (( prj.path == null )
					&& ( prj.inputLists.get(currentInput).size() == 0 )
					&& !prj.useCustomeInputRoot(currentInput) )
				{
					// Set the root and the parameters folder to the file's directory
					String root = Util.getDirectoryName(path);
					prj.setInputRoot(currentInput, root, true);
					chkUseCustomParametersFolder.setSelection(true);
					edParamsFolder.setText(root);
					prj.setCustomParametersFolder(root);
					prj.setUseCustomParametersFolder(true);
					customFilterConfigsNeedUpdate = true;
					resetDisplay(currentInput);
				}
				
				switch ( prj.addDocument(currentInput, path, res[0], null, res[1], allowDup) ) {
				case 0: // OK
					n++;
					break;
				case 1: // Bad root
					// Tell the user
					Dialogs.showError(shell, String.format(Res.getString("MainForm.42"), path), null); //$NON-NLS-1$
					return n;
				case 3: // Root was adjusted
					// Reset the root display
					resetDisp = true;
					break;
				default:
					break;
				}
			}
		}
		// Update the root display if needed
		if ( resetDisp ) {
			resetDisplay(currentInput);
		}
		return n;
	}
	
	private void editInputProperties (int index) {
		Table table = null;
		try {
			if ( currentInput == -1 ) return;
			table = inputTables.get(currentInput);
			saveSurfaceData();
			int n = index;
			if ( n < 0 ) {
				if ( (n = getFocusedInputIndex()) < 0 ) return;
			}
			
			Input inp = prj.getItemFromRelativePath(currentInput,
				table.getItem(n).getText(0));

			// Call the dialog
			updateCustomConfigurations();
			InputPropertiesForm dlg = new InputPropertiesForm(shell, help, fcMapper, prj, prj.getProjectFolder());
			dlg.setData(inp.filterConfigId, inp.sourceEncoding, inp.targetEncoding, fcMapper);
			String[] aRes = dlg.showDialog();
			if ( aRes == null ) return;

			// Update the file(s) data
			if ( aRes[3] != null ) prj.isModified = true;
			startWaiting(Res.getString("MainForm.updatingProject"), false); //$NON-NLS-1$
			if ( index < 0 ) {
				int[] indices = table.getSelectionIndices();
				for ( int i=0; i<indices.length; i++ ) {
					inp = prj.getItemFromRelativePath(currentInput,
						table.getItem(indices[i]).getText(0));
					inp.filterConfigId = aRes[0];
					inp.sourceEncoding = aRes[1];
					inp.targetEncoding = aRes[2];
				}
			}
			else {
				inp = prj.getItemFromRelativePath(currentInput,
					table.getItem(index).getText(0));
				inp.filterConfigId = aRes[0];
				inp.sourceEncoding = aRes[1];
				inp.targetEncoding = aRes[2];
			}
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			if ( table != null ) {
				inputTableMods.get(currentInput).updateTable(table.getSelectionIndices(), 0);
			}
			stopWaiting();
		}
	}

	private void openDocument (int index) {
		try {
			if ( currentInput == -1 ) return;
			if ( index < 0 ) {
				if ( (index = getFocusedInputIndex()) < 0 ) return;
			}
			Input inp = prj.getItemFromRelativePath(currentInput,
				inputTables.get(currentInput).getItem(index).getText(0));
			File file = new File(prj.getInputRoot(currentInput) + File.separator + inp.relativePath);

			String ext = Util.getExtension(inp.relativePath);
			if ( ext.equalsIgnoreCase(Manifest.MANIFEST_EXTENSION) ) {
				// Use the manifest editor if it's a manifest file
				Manifest mnf = new Manifest();
				mnf.load(file);
				ManifestDialog dlg = new ManifestDialog();
				dlg.edit(shell, mnf, false);
			}
			else if ( ext.equalsIgnoreCase(net.sf.okapi.filters.transifex.Project.PROJECT_EXTENSION) ) {
				// Use the Transifex project editor if it's a Transifex project file
				saveSurfaceData(); // Make sure we have the correct source/target locales
				net.sf.okapi.filters.transifex.Project txprj = new net.sf.okapi.filters.transifex.Project();
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
				txprj.read(br, prj.getSourceLanguage(), prj.getTargetLanguage());
				txprj.setPath(file.getCanonicalPath());
				net.sf.okapi.filters.transifex.ui.ProjectDialog dlg = new net.sf.okapi.filters.transifex.ui.ProjectDialog();
				dlg.edit(shell, txprj, false);
			}
			else { // Other types of file
				Program.launch(file.getCanonicalPath());
			}
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void createDocument () {
		try {
			String paths[] = new String[1];
			paths[0] = Dialogs.browseFilenamesForSave(shell, "Create New Document", null, null,
				"All Files (*.*)", "*.*");
			if ( Util.isEmpty(paths[0]) ) return;
			File file = new File(paths[0]);
			file.createNewFile();
			addDocumentsFromList(paths);
			// Select the new entry
			int index = inputTables.get(currentInput).getItemCount()-1;
			if ( index > -1 ) {
				inputTableMods.get(currentInput).updateTable(null, index);
				updateCommands();
			}
		}
		catch ( Throwable e ) {
			Dialogs.showError(shell, "Error creating file\n"+e.getMessage(), null);
		}
	}
	
	private void openContainingFolder (int index) {
		try {
			if ( currentInput == -1 ) return;
			if ( index < 0 ) {
				if ( (index = getFocusedInputIndex()) < 0 ) return;
			}
			Input inp = prj.getItemFromRelativePath(currentInput,
				inputTables.get(currentInput).getItem(index).getText(0));
			File file = new File(prj.getInputRoot(currentInput) + File.separator + inp.relativePath);
			Program.launch(Util.getDirectoryName(file.getCanonicalPath()));
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void editSegmentationRules (String path) {
		SRXEditor dlg = null;
		try {
			dlg = new SRXEditor(shell, true, help);
			dlg.showDialog(path);
		}
		catch ( Throwable e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			if ( dlg != null ) dlg.dispose();
		}
	}
	
	private void runQualityChecker () {
		IQualityCheckEditor dlg = null;
		try {
			saveSurfaceData();
			// Create the dialog
			dlg = new QualityCheckEditor();
			dlg.initialize(shell, true, help, fcMapper, null);
			// Load the current input to the session (if user wants)
			if ( prj.getList(0).size() > 0 ) {
				// Ask to the user first
				// Ask confirmation
				MessageBox msgDlg = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
				msgDlg.setMessage(Res.getString("MainForm.askAddDocsToQCSession")); //$NON-NLS-1$
				msgDlg.setText(Res.getString("MainForm.qcSessionCaption"));
				switch ( msgDlg.open() ) {
				case SWT.CANCEL:
					return; // Stop here
				case SWT.YES:
					// Set the source and target locales
					dlg.getSession().setSourceLocale(prj.getSourceLanguage());
					dlg.getSession().setTargetLocale(prj.getTargetLanguage());
					// Load the documents
					for ( Input item : prj.getList(0) ) {
						String inputPath = prj.getInputRoot(0) + File.separator + item.relativePath;
						File f = new File(inputPath);
						RawDocument rd = new RawDocument(f.toURI(), prj.buildSourceEncoding(item),
							prj.getSourceLanguage(), prj.getTargetLanguage());
						rd.setFilterConfigId(item.filterConfigId);
						dlg.addRawDocument(rd);
					}
					break;
				}
			}
			// Start the dialog
			dlg.edit(true);
		}
		catch ( Throwable e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			if ( dlg != null ) dlg = null;
		}
	}
	
	private void runTestingConsole () {
		PairEditorUserTest dlg = null;
		try {
			saveSurfaceData();
			updateCustomConfigurations();
			dlg = new PairEditorUserTest(shell, fcMapper, true);
			dlg.showDialog();
		}
		catch ( Throwable e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			if ( dlg != null ) dlg = null;
		}
	}
	
	private void filterConfigurations () {
		try {
			saveSurfaceData();
			updateCustomConfigurations();
			FilterConfigurationsDialog dlg = new FilterConfigurationsDialog(shell, false, fcMapper, help); 
			updateCustomConfigurations();
			dlg.showDialog(null);
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}

	private void setupPipelineWrapper () {
		saveSurfaceData();
		updateCustomConfigurations();
		if ( wrapper == null ) {
			wrapper = new PipelineWrapper(fcMapper, appRootFolder, pm, prj.getProjectFolder(),
					prj.getInputRoot(0), prj.buildOutputRoot(0), shell, context);
		}
		else { // Make sure to reset the root dir each time
			wrapper.setRootDirectories(prj.getProjectFolder(), prj.getInputRoot(0));
		}
	}
	
	private void exportBatchConfiguration () {
		try {
			setupPipelineWrapper();
			String path = Dialogs.browseFilenamesForSave(shell, "Save Batch Configuration", null, null,
				"Batch Configuration Files (*.bconf)", "*.bconf");
			if ( Util.isEmpty(path) ) return;
			// Else: export
			// Get the current pipeline of the project
			BatchConfiguration bc = new BatchConfiguration();
			wrapper.loadFromStringStorageOrReset(prj.getUtilityParameters(PRJPIPELINEID));
			updatePluginsAndDependencies(); // Update plugin list in the wrapper's pm with pm.discover()
			bc.exportConfiguration(path, wrapper, fcMapper, prj.inputLists.get(0));
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void installBatchConfiguration () {
		try {
			setupPipelineWrapper();
			// Get the batch configuration file
			String[] paths = Dialogs.browseFilenames(shell, "Install Batch Configuration", false, null,
				"Batch Configuration Files (*.bconf)", "*.bconf");
			if ( paths == null ) return;
			// Select the output directory
			InputDialog dlg = new InputDialog(shell, "Batch Configuration Installation",
				"Directory where the batch configuration should be installed", 
				prj.getInputRoot(currentInput), null, 1, -1, -1);
			String outputDir = dlg.showDialog();
			if ( outputDir == null ) return; // Canceled
			// Else: install
			BatchConfiguration bc = new BatchConfiguration();
			bc.installConfiguration(paths[0], outputDir, wrapper);
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void pluginsManager () {
		try {
			saveSurfaceData();
			File dir = new File(getDropinsDirectory());
			PluginsManagerDialog dlg = new PluginsManagerDialog(shell, help, dir, null);
			if ( !dlg.showDialog() ) return; // Nothing was changed

			// Otherwise; make sure to update the dependencies
			pm.discover(dir, false);
			updatePluginsAndDependencies();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void showCharInfo () {
		try {
			CharacterInfoDialog dlg = new CharacterInfoDialog(shell, Res.getString("MainForm.charInfoCaption"), help); //$NON-NLS-1$
			dlg.showDialog(0x9F99);
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void showCodeFinderEditor () {
		try {
			CodeFinderEditor dlg = new CodeFinderEditor(shell, help);
			dlg.showDialog();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void listEncodings () {
		try {
			int count = 0;
			int countAlias = 0;
			startWaiting(Res.getString("MainForm.listingEncodings"), true); //$NON-NLS-1$
			log.beginTask(Res.getString("MainForm.listEncodingsTask")); //$NON-NLS-1$
			SortedMap<String, Charset> charsets = Charset.availableCharsets();
			for ( String key : charsets.keySet() ) {
				log.message(charsets.get(key).displayName());
				count++;
				Set<String> aliases = charsets.get(key).aliases();
				for ( String alias : aliases ) {
					log.message("\t" + alias); //$NON-NLS-1$
					countAlias++;
				}
			}
			log.message(String.format(Res.getString("MainForm.numEncodings"), count)); //$NON-NLS-1$
			log.message(String.format(Res.getString("MainForm.numAliases"), count+countAlias)); //$NON-NLS-1$
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			log.endTask(null);
			stopWaiting();
			log.show();
		}
	}
	
	private void removeDocuments (int index) {
		boolean refresh = false;
		try {
			if ( index < 0 ) {
				if ( getFocusedInputIndex() < 0 ) return;
			}
			// Ask confirmation
			MessageBox dlg = new MessageBox(shell, SWT.ICON_QUESTION | SWT.YES | SWT.NO);
			dlg.setMessage(Res.getString("MainForm.askForRemove")); //$NON-NLS-1$
			dlg.setText(APPNAME);
			if ( dlg.open() != SWT.YES ) return;

			refresh = true;
			Input inp;
			startWaiting(Res.getString("MainForm.updatingProject"), false); //$NON-NLS-1$
			Table table = inputTables.get(currentInput);
			if ( index < 0 ) {
				int[] indices = table.getSelectionIndices();
				index = indices[0];
				for ( int i=0; i<indices.length; i++ ) {
					inp = prj.getItemFromRelativePath(currentInput, table.getItem(indices[i]).getText(0));
					prj.getList(currentInput).remove(inp);
				}
			}
			else {
				inp = prj.getItemFromRelativePath(currentInput, table.getItem(index).getText(0));
				prj.getList(currentInput).remove(inp);
			}
			prj.isModified = true;
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
		finally {
			if ( refresh ) {
				inputTableMods.get(currentInput).updateTable(null, index);
				updatePathBuilderSampleData();
				updateCommands();
				stopWaiting();
			}
		}
	}

	private void moveDocumentsUp () {
		try {
			if ( currentInput == -1 ) return;
			if ( getFocusedInputIndex() < 0 ) return;
			saveSurfaceData();
			// Get the selected documents
			Table table = inputTables.get(currentInput);
			int[] indices = table.getSelectionIndices();
			
			// Make sure the selection covers all items from first to last
			int first = indices[0];
			if ( first == 0 ) return; // Already at the top
			int last = indices[indices.length-1];
			table.select(first, last);
			indices = table.getSelectionIndices();

			// Move the document before first to position after last
			Input inp = prj.getItemFromRelativePath(currentInput, table.getItem(first-1).getText(0));
			prj.getList(currentInput).remove(inp);
			prj.getList(currentInput).add(last, inp);
			prj.isModified = true;

			// Update the selection
			for ( int i=0; i<indices.length; i++ ) {
				indices[i] = indices[i]-1;
			}
			inputTableMods.get(currentInput).updateTable(indices, 0);
			updatePathBuilderSampleData();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}
	
	private void moveDocumentsDown () {
		try {
			if ( currentInput == -1 ) return;
			if ( getFocusedInputIndex() < 0 ) return;
			saveSurfaceData();
			// Get the selected documents
			Table table = inputTables.get(currentInput);
			int[] indices = table.getSelectionIndices();
			
			// Make sure the selection covers all items from first to last
			int last = indices[indices.length-1];
			if ( last >= table.getItemCount()-1 ) return; // Already at the bottom
			int first = indices[0];
			table.select(first, last);
			indices = table.getSelectionIndices();

			// Move the document after last to position before first
			Input inp = prj.getItemFromRelativePath(currentInput, table.getItem(last+1).getText(0));
			prj.getList(currentInput).remove(inp);
			prj.getList(currentInput).add(first, inp);
			prj.isModified = true;

			// Update the selection
			for ( int i=0; i<indices.length; i++ ) {
				indices[i] = indices[i]+1;
			}
			inputTableMods.get(currentInput).updateTable(indices, 0);
			updatePathBuilderSampleData();
		}
		catch ( Exception e ) {
			Dialogs.showError(shell, e.getMessage(), null);
		}
	}

}
