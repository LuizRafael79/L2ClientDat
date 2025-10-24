/*
 * This file is part of the L2ClientDat project.
 * * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.Box; // NECESSÁRIO PARA ESPAÇAMENTO VERTICAL
import javax.swing.BoxLayout; // NECESSÁRIO PARA SIDEBAR
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.LookAndFeel; // NECESSÁRIO PARA TROCAR TEMA
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer; // Para o relógio da animação
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

import org.l2jmobius.actions.ActionTask;
import org.l2jmobius.actions.MassRecryptor;
import org.l2jmobius.actions.MassTxtPacker;
import org.l2jmobius.actions.MassTxtUnpacker;
import org.l2jmobius.actions.OpenDat;
import org.l2jmobius.actions.SaveDat;
import org.l2jmobius.actions.SaveTxt;
import org.l2jmobius.clientcryptor.crypt.DatCrypter;
import org.l2jmobius.config.ConfigDebug;
import org.l2jmobius.config.ConfigWindow;
import org.l2jmobius.forms.JPopupTextArea;
import org.l2jmobius.util.Util;
import org.l2jmobius.xml.CryptVersionParser;
import org.l2jmobius.xml.DescriptorParser;

public class L2ClientDat extends JFrame
{
	private static final Logger LOGGER = Logger.getLogger(L2ClientDat.class.getName());
	
	private static final String DAT_STRUCTURE_STR = "Structures";
	public static final String ENABLED_STR = "Enabled";
	public static final String DISABLED_STR = "Disabled";
	private static final String OPEN_BTN_STR = "Open";
	private static final String SAVE_TXT_BTN_STR = "Save (TXT)";
	private static final String SAVE_DAT_BTN_STR = "Save (DAT)";
	private static final String DECRYPT_ALL_BTN_STR = "Unpack all";
	private static final String ENCRYPT_ALL_BTN_STR = "Pack all";
	private static final String PATCH_ALL_BTN_STR = "Patch all";
	private static final String SELECT_BTN_STR = "Select";
	private static final String FILE_SELECT_BTN_STR = "File select";
	private static final String ABORT_BTN_STR = "Abort";
	private static final String SOURCE_ENCRYPT_TYPE_STR = "Source";
	public static final String DETECT_STR = "Detect";
	public static final String NO_TRANSLATE_STR = "No translate";
	public static final String TOGGLE_THEME_BTN_STR = "Toogle theme";
	
	public static boolean DEV_MODE = false;
	
	private static JTextArea _textPaneLog;
	private final ExecutorService _executorService = Executors.newCachedThreadPool();
	private final JPopupTextArea _textPaneMain;
	private final LineNumberingTextArea _lineNumberingTextArea;
	private final JComboBox<String> _jComboBoxChronicle;
	private final JComboBox<String> _jComboBoxEncrypt;
	private final JComboBox<String> _jComboBoxFormatter;
	private final ArrayList<JPanel> _actionPanels = new ArrayList<>(); // GUARDA OS PAINEIS COM CONTROLES A DESABILITAR
	private final JButton _saveTxtButton;
	private final JButton _saveDatButton;
	private final JButton _abortTaskButton;
	private final JProgressBar _progressBar;
	
	private File _currentFileWindow = null;
	private ActionTask _progressTask = null;
	
	private boolean isDarkTheme = false; // Initial state depends on startup logic
	
	/**
	 * MÉTODO MAIN CORRIGIDO E COMPLETO Carrega Configs PRIMEIRO, depois usa Timer/SwingWorker
	 */
	public static void main(String[] args)
	{
		// ETAPA 1: Carregar as Configurações ANTES de tudo
		// Isso garante que THEME_PREFERENCE tenha o valor correto do .ini
		ConfigWindow.load();
		ConfigDebug.load(); // Carrega o debug config também, por consistência
		
		// ETAPA 2: Agendar a inicialização da UI no thread do Swing
		EventQueue.invokeLater(() ->
		{
			// Agora podemos ler a preferência CARREGADA
			boolean startDark = "dark".equalsIgnoreCase(ConfigWindow.THEME_PREFERENCE);
			
			// Configura o Look & Feel (Tema) PRIMEIRO.
			try
			{
				// Define o tema baseado na preferência carregada
				UIManager.setLookAndFeel(startDark ? new FlatMacDarkLaf() : new FlatMacLightLaf());
			}
			catch (Exception ex)
			{
				LOGGER.log(Level.SEVERE, "Failed to initialize FlatLaf. Falling back to default.", ex);
				startDark = false; // Garante fallback para claro
				try
				{
					UIManager.setLookAndFeel(new FlatMacLightLaf());
				}
				catch (Exception ignored)
				{
				}
			}
			
			// Cria o NOSSO splash screen (mas ainda não mostra)
			final ModernSplashWindow splash = new ModernSplashWindow();
			
			// --- CONTROLE DE TAREFAS ---
			final AtomicBoolean loadingFinished = new AtomicBoolean(false);
			final AtomicBoolean animationFinished = new AtomicBoolean(false);
			final AtomicReference<L2ClientDat> mainAppRef = new AtomicReference<>(null);
			final boolean finalStartDark = startDark; // Para usar dentro da lambda
			
			// Função que será chamada quando CADA tarefa terminar
			final Runnable tryFinishSplash = () ->
			{
				if (animationFinished.get() && loadingFinished.get())
				{
					L2ClientDat mainApp = mainAppRef.get();
					if (mainApp != null)
					{
						splash.dispose();
						mainApp.setVisible(true);
						mainApp.toFront();
					}
				}
			};
			
			// --- TAREFA 1: O CARREGADOR (SwingWorker) ---
			// (Agora SÓ carrega os parsers e constrói o app)
			class LoadingTask extends SwingWorker<L2ClientDat, Void>
			{
				@Override
				protected L2ClientDat doInBackground() throws Exception
				{
					// 1. Configurar Logs e DevMode (Já não carrega configs aqui)
					final File logFolder = new File(".", "log");
					logFolder.mkdir();
					try (InputStream is = new FileInputStream(new File("./config/log.cfg")))
					{
						LogManager.getLogManager().readConfiguration(is);
					}
					catch (Exception e)
					{
						LOGGER.log(Level.SEVERE, null, e);
					}
					DEV_MODE = Util.contains((Object[]) args, (Object) "-dev");
					
					// 2. Fazer Parse dos arquivos (Configs já foram carregadas)
					CryptVersionParser.getInstance().parse();
					DescriptorParser.getInstance().parse();
					
					// 3. PRÉ-CONSTRUIR a janela principal (passando o tema correto)
					return new L2ClientDat(finalStartDark);
				}
				
				@Override
				protected void done()
				{
					try
					{
						mainAppRef.set(get()); // Salva a janela pronta
					}
					catch (Exception e)
					{
						LOGGER.log(Level.SEVERE, "Falha crítica ao criar janela principal", e);
						System.exit(1);
					}
					
					loadingFinished.set(true); // Avisa: O CARREGAMENTO TERMINOU
					tryFinishSplash.run(); // Tenta fechar o splash
				}
			}
			
			// --- TAREFA 2: O ANIMADOR (Timer) ---
			final int ANIMATION_MS = 1000;
			final int MIN_WAIT_MS = 1500; // Tempo mínimo que o splash fica visível
			final int REFRESH_MS = 15;
			final AtomicInteger elapsed = new AtomicInteger(0);
			
			splash.setVisible(true); // Mostra o splash
			
			final Timer animationTimer = new Timer(REFRESH_MS, null);
			animationTimer.addActionListener(e ->
			{
				int time = elapsed.addAndGet(REFRESH_MS);
				double animProgress = Math.min(1.0, (double) time / ANIMATION_MS);
				double easedProgress = 1 - Math.pow(1 - animProgress, 3);
				splash.setAnimationProgress(easedProgress);
				
				if ((time >= MIN_WAIT_MS) && loadingFinished.get())
				{
					animationTimer.stop();
					animationFinished.set(true);
					tryFinishSplash.run();
				}
			});
			
			animationTimer.start();
			new LoadingTask().execute();
		});
	}
	
	// CONSTRUTOR MODIFICADO para aceitar o estado inicial do tema
	public L2ClientDat(boolean startDark)
	{
		this.isDarkTheme = startDark;
		
		// Inicializa componentes (como antes)
		_jComboBoxChronicle = new JComboBox<>();
		_jComboBoxEncrypt = new JComboBox<>();
		_jComboBoxFormatter = new JComboBox<>();
		_saveTxtButton = new JButton();
		_saveDatButton = new JButton();
		_progressBar = new JProgressBar(0, 100);
		_abortTaskButton = new JButton();
		_textPaneMain = new JPopupTextArea();
		_lineNumberingTextArea = new LineNumberingTextArea(_textPaneMain);
		_textPaneLog = new JPopupTextArea();
		
		// Constrói a NOVA UI
		initComponents();
		
		// Configura o Frame (como antes)
		setupFrame();
	}
	
	// CONSTRUTOR ANTIGO (mantido para compatibilidade, caso seja chamado de outro lugar)
	@Deprecated
	public L2ClientDat()
	{
		this(false); // Assume tema claro se não especificado
	}
	
	/**
	 * REWORK: Cria a NOVA estrutura da UI (Sidebar + Main Panel).
	 */
	private void initComponents()
	{
		// Define o layout principal do JFrame
		getContentPane().setLayout(new BorderLayout(5, 0)); // Espaçamento horizontal
		getContentPane().add(createSidebarPanel(), BorderLayout.WEST);
		getContentPane().add(createMainPanel(), BorderLayout.CENTER);
	}
	
	/**
	 * REWORK: Cria o painel da barra lateral esquerda.
	 */
	/**
	 * REWORK: Cria o painel da barra lateral esquerda. **NOVA VERSÃO:** Garante que todos os botões de ação tenham a mesma largura.
	 */
	/**
	 * REWORK: Cria o painel da barra lateral esquerda. **VERSÃO CORRIGIDA:** Garante mesma largura, mantendo altura natural.
	 */
	/**
	 * REWORK: Cria o painel da barra lateral esquerda. **VERSÃO COM DIVISORES:** Adiciona JSeparators entre grupos de botões.
	 */
	private JPanel createSidebarPanel()
	{
		JPanel sidebarPanel = new JPanel();
		sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
		sidebarPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));
		
		// --- Sub-painel para os botões de ação ---
		JPanel actionsButtonPanel = new JPanel();
		actionsButtonPanel.setLayout(new BoxLayout(actionsButtonPanel, BoxLayout.Y_AXIS));
		
		// 1. Cria os botões (como antes)
		JButton openButton = new JButton(OPEN_BTN_STR);
		openButton.addActionListener(this::openSelectFileWindow);
		
		_saveTxtButton.setText(SAVE_TXT_BTN_STR);
		_saveTxtButton.addActionListener(this::saveTxtActionPerformed);
		_saveTxtButton.setEnabled(false);
		
		_saveDatButton.setText(SAVE_DAT_BTN_STR);
		_saveDatButton.addActionListener(this::saveDatActionPerformed);
		_saveDatButton.setEnabled(false);
		
		JButton massUnpackButton = new JButton(DECRYPT_ALL_BTN_STR);
		massUnpackButton.addActionListener(this::massTxtUnpackActionPerformed);
		
		JButton massPackButton = new JButton(ENCRYPT_ALL_BTN_STR);
		massPackButton.addActionListener(this::massTxtPackActionPerformed);
		
		JButton massRecryptButton = new JButton(PATCH_ALL_BTN_STR);
		massRecryptButton.addActionListener(this::massRecryptActionPerformed);
		
		JButton themeToggleButton = new JButton(TOGGLE_THEME_BTN_STR);
		themeToggleButton.addActionListener(e -> toggleTheme());
		
		// 2. Lista de botões para redimensionar (como antes)
		List<JButton> buttonsToResize = new ArrayList<>();
		buttonsToResize.add(openButton);
		buttonsToResize.add(_saveTxtButton);
		buttonsToResize.add(_saveDatButton);
		buttonsToResize.add(massUnpackButton);
		buttonsToResize.add(massPackButton);
		buttonsToResize.add(massRecryptButton);
		// buttonsToResize.add(themeToggleButton);
		
		// 3. Calcula largura máxima (como antes)
		int maxWidth = 0;
		for (JButton button : buttonsToResize)
		{
			maxWidth = Math.max(maxWidth, button.getPreferredSize().width);
		}
		
		// 4. Define tamanho máximo (como antes)
		for (JButton button : buttonsToResize)
		{
			Dimension fixedWidthSize = new Dimension(maxWidth, button.getPreferredSize().height);
			button.setMaximumSize(fixedWidthSize);
			button.setPreferredSize(fixedWidthSize);
			button.setMinimumSize(fixedWidthSize);
			button.setAlignmentX(Component.CENTER_ALIGNMENT);
		}
		
		if (!buttonsToResize.contains(themeToggleButton))
		{
			themeToggleButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		}
		
		// 5. Adiciona botões, espaçadores E SEPARADORES ao painel
		final int smallSpacing = 5; // Espaçamento menor em volta dos separadores
		final int largeSpacing = 12; // Espaçamento maior entre grupos
		
		// Grupo Open/Save
		actionsButtonPanel.add(openButton);
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing))); // Espaço
		actionsButtonPanel.add(new JSeparator()); // <-- DIVISOR AQUI
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing))); // Espaço
		actionsButtonPanel.add(_saveTxtButton);
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing))); // Espaço
		actionsButtonPanel.add(_saveDatButton);
		
		// Espaço maior antes do próximo grupo
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, largeSpacing)));
		actionsButtonPanel.add(new JSeparator()); // <-- DIVISOR AQUI
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing))); // Espaço
		
		// Grupo Mass Actions
		actionsButtonPanel.add(massUnpackButton);
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing))); // Espaço
		actionsButtonPanel.add(massPackButton);
		actionsButtonPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing))); // Espaço
		actionsButtonPanel.add(massRecryptButton);
		
		// Adiciona o sub-painel à barra lateral
		sidebarPanel.add(actionsButtonPanel);
		sidebarPanel.add(Box.createVerticalGlue()); // Empurra pra baixo
		
		// Adiciona separador antes do botão de tema (opcional)
		sidebarPanel.add(new JSeparator());
		sidebarPanel.add(Box.createRigidArea(new Dimension(0, smallSpacing)));
		
		sidebarPanel.add(themeToggleButton); // Botão de tema no final
		
		_actionPanels.add(actionsButtonPanel);
		
		return sidebarPanel;
	}
	
	/**
	 * REWORK: Cria o painel principal à direita.
	 */
	private JPanel createMainPanel()
	{
		JPanel mainPanel = new JPanel(new BorderLayout(0, 5)); // Espaçamento vertical
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10)); // Padding
		
		JPanel settingsPanel = createSettingsPanel(); // Cria painel de configurações
		JSplitPane editorSplitPane = createEditorSplitPane(); // Cria painel dos editores
		JPanel progressPanel = createProgressPanel(); // Cria painel de progresso
		
		mainPanel.add(settingsPanel, BorderLayout.NORTH);
		mainPanel.add(editorSplitPane, BorderLayout.CENTER);
		mainPanel.add(progressPanel, BorderLayout.SOUTH);
		
		// Adiciona o painel de configurações à lista de painéis a serem gerenciados
		_actionPanels.add(settingsPanel);
		
		return mainPanel;
	}
	
	/**
	 * REWORK: Cria o painel de configurações (Chronicle, Encrypt, Formatter).
	 */
	private JPanel createSettingsPanel()
	{
		JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		// settingsPanel.setBorder(BorderFactory.createTitledBorder("Configurações")); // Borda opcional
		
		final JLabel structureLabel = new JLabel(DAT_STRUCTURE_STR);
		settingsPanel.add(structureLabel);
		final String[] chronicles = DescriptorParser.getInstance().getChronicleNames().toArray(new String[0]);
		_jComboBoxChronicle.setModel(new DefaultComboBoxModel<>(chronicles));
		_jComboBoxChronicle.setSelectedItem(Util.contains((Object[]) chronicles, (Object) ConfigWindow.CURRENT_CHRONICLE) ? ConfigWindow.CURRENT_CHRONICLE : chronicles[chronicles.length - 1]);
		_jComboBoxChronicle.addActionListener(e -> saveComboBox(_jComboBoxChronicle, "CURRENT_CHRONICLE"));
		settingsPanel.add(_jComboBoxChronicle);
		
		settingsPanel.add(Box.createRigidArea(new Dimension(20, 0))); // Espaçador
		
		final JLabel encryptLabel = new JLabel("Encrypt:");
		settingsPanel.add(encryptLabel);
		DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>(CryptVersionParser.getInstance().getEncryptKey().keySet().toArray(new String[0]));
		comboBoxModel.insertElementAt(SOURCE_ENCRYPT_TYPE_STR, 0);
		// comboBoxModel.setSelectedItem(SOURCE_ENCRYPT_TYPE_STR); // Removido para carregar config
		_jComboBoxEncrypt.setModel(comboBoxModel);
		_jComboBoxEncrypt.setSelectedItem(ConfigWindow.CURRENT_ENCRYPT); // Carrega a config salva
		_jComboBoxEncrypt.addActionListener(e -> saveComboBox(_jComboBoxEncrypt, "CURRENT_ENCRYPT"));
		settingsPanel.add(_jComboBoxEncrypt);
		
		settingsPanel.add(Box.createRigidArea(new Dimension(20, 0))); // Espaçador
		
		final JLabel inputFormatterLabel = new JLabel("Formatter:");
		settingsPanel.add(inputFormatterLabel);
		comboBoxModel = new DefaultComboBoxModel<>(new String[]
		{
			"Enabled",
			"Disabled"
		});
		_jComboBoxFormatter.setModel(comboBoxModel);
		_jComboBoxFormatter.setSelectedItem(ConfigWindow.CURRENT_FORMATTER);
		_jComboBoxFormatter.addActionListener(e -> saveComboBox(_jComboBoxFormatter, "CURRENT_FORMATTER"));
		settingsPanel.add(_jComboBoxFormatter);
		
		return settingsPanel;
	}
	
	/**
	 * REWORK: Cria o painel de progresso (Barra + Botão Abort).
	 */
	private JPanel createProgressPanel()
	{
		JPanel progressPanel = new JPanel(new BorderLayout(10, 0));
		// progressPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0)); // Padding Top
		
		_progressBar.setPreferredSize(new Dimension(100, 20)); // Altura menor
		_progressBar.setValue(0);
		_progressBar.setStringPainted(true);
		progressPanel.add(_progressBar, BorderLayout.CENTER);
		
		_abortTaskButton.setText(ABORT_BTN_STR);
		_abortTaskButton.addActionListener(this::abortActionPerformed);
		_abortTaskButton.setEnabled(false);
		progressPanel.add(_abortTaskButton, BorderLayout.EAST);
		
		return progressPanel;
	}
	
	/**
	 * REWORK: Cria o JSplitPane com os dois editores de texto.
	 */
	private JSplitPane createEditorSplitPane()
	{
		final Font font = new Font(new JLabel().getFont().getName(), 1, 13);
		
		// --- Editor Principal ---
		_textPaneMain.setBackground(new Color(41, 49, 52)); // Mantido customizado
		_textPaneMain.setForeground(Color.WHITE); // Mantido customizado
		_textPaneMain.setFont(font);
		((AbstractDocument) _textPaneMain.getDocument()).setDocumentFilter(new DocumentFilter()
		{
			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
			{
				final String replacedText = text.replace("\r\n", "\n");
				super.replace(fb, offset, length, replacedText, attrs);
			}
		});
		
		// --- Números de Linha ---
		_lineNumberingTextArea.setFont(font.deriveFont(12.0f));
		_lineNumberingTextArea.setEditable(false);
		_textPaneMain.getDocument().addDocumentListener(_lineNumberingTextArea);
		
		final JScrollPane scrollPaneEditor = new JScrollPane(_textPaneMain);
		scrollPaneEditor.setRowHeaderView(_lineNumberingTextArea);
		scrollPaneEditor.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		
		// --- Console de Log ---
		_textPaneLog.setBackground(new Color(41, 49, 52)); // Mantido customizado
		_textPaneLog.setForeground(Color.CYAN); // Mantido customizado
		_textPaneLog.setEditable(false);
		_textPaneLog.setFont(font.deriveFont(11f)); // Fonte um pouco menor para o log
		
		final JScrollPane scrollPaneLog = new JScrollPane(_textPaneLog);
		scrollPaneLog.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		
		// --- Split Pane ---
		final JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPaneEditor, scrollPaneLog);
		jsp.setResizeWeight(0.7);
		jsp.setOneTouchExpandable(true);
		// jsp.setBorder(BorderFactory.createEmptyBorder()); // Sem borda interna
		
		return jsp;
	}
	
	/**
	 * REWORK: Configura o JFrame principal (título, tamanho, ícones, etc.). Removido setVisible e toFront daqui.
	 */
	private void setupFrame()
	{
		setTitle("L2ClientDat Editor");
		setMinimumSize(new Dimension(1000, 600));
		// Usa os tamanhos salvos OU um padrão maior
		setSize(new Dimension(Math.max(1100, ConfigWindow.WINDOW_WIDTH), Math.max(700, ConfigWindow.WINDOW_HEIGHT)));
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // Usar constante' é melhor
		setLocationRelativeTo(null);
		
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent evt)
			{
				// Salva as dimensões atuais ao fechar
				ConfigWindow.save("WINDOW_HEIGHT", String.valueOf(getHeight()));
				ConfigWindow.save("WINDOW_WIDTH", String.valueOf(getWidth()));
				ConfigWindow.save("THEME_PREFERENCE", isDarkTheme ? "dark" : "light"); // Salva o tema
				System.exit(0);
			}
		});
		
		final List<Image> icons = new ArrayList<>();
		// Tenta carregar ícones, mas não quebra se não encontrar
		try
		{
			icons.add(new ImageIcon("." + File.separator + "images" + File.separator + "l2jmobius_16x16.png").getImage());
			icons.add(new ImageIcon("." + File.separator + "images" + File.separator + "l2jmobius_32x32.png").getImage());
			icons.add(new ImageIcon("." + File.separator + "images" + File.separator + "l2jmobius_64x64.png").getImage());
			icons.add(new ImageIcon("." + File.separator + "images" + File.separator + "l2jmobius_128x128.png").getImage());
			setIconImages(icons);
		}
		catch (Exception e)
		{
			LOGGER.warning("Não foi possível carregar os ícones da aplicação.");
		}
		
		pack(); // Ajusta o tamanho inicial com base nos componentes
	}
	
	/**
	 * REWORK: Lógica para trocar o tema dinamicamente.
	 */
	private void toggleTheme()
	{
		isDarkTheme = !isDarkTheme; // Inverte o estado
		try
		{
			LookAndFeel newLaf = isDarkTheme ? new FlatMacDarkLaf() : new FlatMacLightLaf();
			UIManager.setLookAndFeel(newLaf);
			
			// Atualiza a UI de TODAS as janelas abertas (incluindo JFileChooser, se houver)
			for (Window window : Window.getWindows())
			{
				SwingUtilities.updateComponentTreeUI(window);
			}
			
			ConfigWindow.save("THEME_PREFERENCE", isDarkTheme ? "dark" : "light");
			
		}
		catch (Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Erro ao trocar o tema.", ex);
			isDarkTheme = !isDarkTheme; // Reverte o estado se der erro
		}
	}
	
	// --- MÉTODOS ORIGINAIS (sem modificação de lógica) ---
	
	public JPopupTextArea getTextPaneMain()
	{
		return _textPaneMain;
	}
	
	public static void addLogConsole(String log, boolean isLog)
	{
		if (isLog)
		{
			LOGGER.info(log);
		}
		
		final String logLine = log + "\n";
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() ->
			{
				_textPaneLog.append(logLine);
				_textPaneLog.setCaretPosition(_textPaneLog.getDocument().getLength()); // Auto-scroll
			});
		}
		else
		{
			_textPaneLog.append(logLine);
			_textPaneLog.setCaretPosition(_textPaneLog.getDocument().getLength()); // Auto-scroll
		}
	}
	
	public void setEditorText(String text)
	{
		_lineNumberingTextArea.cleanUp();
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() ->
			{
				_textPaneMain.setText(text);
				_textPaneMain.setCaretPosition(0); // Volta cursor pro início
			});
		}
		else
		{
			_textPaneMain.setText(text);
			_textPaneMain.setCaretPosition(0);
		}
	}
	
	private void massTxtPackActionPerformed(ActionEvent evt)
	{
		if (_progressTask != null)
		{
			return;
		}
		
		final JFileChooser fileopen = new JFileChooser();
		fileopen.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // Apenas diretórios
		fileopen.setAcceptAllFileFilterUsed(false);
		fileopen.setCurrentDirectory(new File(ConfigWindow.FILE_OPEN_CURRENT_DIRECTORY_PACK));
		fileopen.setPreferredSize(new Dimension(700, 500)); // Um pouco maior
		
		final int ret = fileopen.showDialog(this, SELECT_BTN_STR); // Passa 'this' como pai
		if (ret == JFileChooser.APPROVE_OPTION)
		{
			_currentFileWindow = fileopen.getSelectedFile();
			ConfigWindow.save("FILE_OPEN_CURRENT_DIRECTORY_PACK", _currentFileWindow.getPath());
			addLogConsole("---------------------------------------", true);
			addLogConsole("Selected folder for packing: " + _currentFileWindow.getPath(), true);
			_progressTask = new MassTxtPacker(this, String.valueOf(_jComboBoxChronicle.getSelectedItem()), _currentFileWindow.getPath());
			_executorService.execute(_progressTask);
		}
	}
	
	private void massTxtUnpackActionPerformed(ActionEvent evt)
	{
		if (_progressTask != null)
		{
			return;
		}
		
		final JFileChooser fileopen = new JFileChooser();
		fileopen.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fileopen.setAcceptAllFileFilterUsed(false);
		fileopen.setCurrentDirectory(new File(ConfigWindow.FILE_OPEN_CURRENT_DIRECTORY_UNPACK));
		fileopen.setPreferredSize(new Dimension(700, 500));
		
		final int ret = fileopen.showDialog(this, SELECT_BTN_STR);
		if (ret == JFileChooser.APPROVE_OPTION)
		{
			_currentFileWindow = fileopen.getSelectedFile();
			ConfigWindow.save("FILE_OPEN_CURRENT_DIRECTORY_UNPACK", _currentFileWindow.getPath());
			addLogConsole("---------------------------------------", true);
			addLogConsole("Selected folder for unpacking: " + _currentFileWindow.getPath(), true);
			_progressTask = new MassTxtUnpacker(this, String.valueOf(_jComboBoxChronicle.getSelectedItem()), _currentFileWindow.getPath());
			_executorService.execute(_progressTask);
		}
	}
	
	private void massRecryptActionPerformed(ActionEvent evt)
	{
		if (_progressTask != null)
		{
			return;
		}
		
		final JFileChooser fileopen = new JFileChooser();
		fileopen.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fileopen.setAcceptAllFileFilterUsed(false);
		fileopen.setCurrentDirectory(new File(ConfigWindow.FILE_OPEN_CURRENT_DIRECTORY));
		fileopen.setPreferredSize(new Dimension(700, 500));
		
		final int ret = fileopen.showDialog(this, SELECT_BTN_STR);
		if (ret == JFileChooser.APPROVE_OPTION)
		{
			_currentFileWindow = fileopen.getSelectedFile();
			ConfigWindow.save("FILE_OPEN_CURRENT_DIRECTORY", _currentFileWindow.getPath());
			addLogConsole("---------------------------------------", true);
			addLogConsole("Selected folder for patching: " + _currentFileWindow.getPath(), true);
			_progressTask = new MassRecryptor(this, _currentFileWindow.getPath());
			_executorService.execute(_progressTask);
		}
	}
	
	private void openSelectFileWindow(ActionEvent evt)
	{
		if (_progressTask != null)
		{
			return;
		}
		
		final JFileChooser fileopen = new JFileChooser();
		fileopen.setFileSelectionMode(JFileChooser.FILES_ONLY); // Apenas arquivos
		fileopen.setMultiSelectionEnabled(false);
		fileopen.setAcceptAllFileFilterUsed(false); // Começa sem "Todos os Arquivos"
		// Adiciona filtros específicos primeiro
		fileopen.addChoosableFileFilter(new FileNameExtensionFilter("Client Files (*.dat, *.ini, *.txt, *.htm)", "dat", "ini", "txt", "htm"));
		fileopen.addChoosableFileFilter(new FileNameExtensionFilter("DAT Files (*.dat)", "dat"));
		fileopen.addChoosableFileFilter(new FileNameExtensionFilter("INI Files (*.ini)", "ini"));
		fileopen.addChoosableFileFilter(new FileNameExtensionFilter("TXT Files (*.txt)", "txt"));
		fileopen.addChoosableFileFilter(new FileNameExtensionFilter("HTM Files (*.htm)", "htm"));
		// Define o filtro inicial como o mais abrangente
		fileopen.setFileFilter(fileopen.getChoosableFileFilters()[0]);
		
		// Tenta definir o diretório/arquivo inicial
		try
		{
			File initialFile = new File(ConfigWindow.LAST_FILE_SELECTED);
			if (initialFile.exists())
			{
				fileopen.setSelectedFile(initialFile);
			}
			else if ((initialFile.getParentFile() != null) && initialFile.getParentFile().exists())
			{
				fileopen.setCurrentDirectory(initialFile.getParentFile());
			}
		}
		catch (Exception e)
		{
			// Ignora se o caminho salvo for inválido
		}
		
		fileopen.setPreferredSize(new Dimension(700, 500));
		
		final int ret = fileopen.showDialog(this, FILE_SELECT_BTN_STR);
		if (ret == JFileChooser.APPROVE_OPTION)
		{
			_currentFileWindow = fileopen.getSelectedFile();
			ConfigWindow.save("LAST_FILE_SELECTED", _currentFileWindow.getAbsolutePath());
			addLogConsole("---------------------------------------", true);
			addLogConsole("Opening file: " + _currentFileWindow.getName(), true);
			_progressTask = new OpenDat(this, String.valueOf(_jComboBoxChronicle.getSelectedItem()), _currentFileWindow);
			_executorService.execute(_progressTask);
		}
	}
	
	private void saveTxtActionPerformed(ActionEvent evt)
	{
		if (_progressTask != null)
		{
			return;
		}
		
		if (_currentFileWindow == null)
		{
			addLogConsole("No file open to save as TXT!", true);
			// Talvez mostrar uma mensagem na UI?
			return;
		}
		
		final JFileChooser fileSave = new JFileChooser();
		// Tenta usar o diretório do arquivo atual ou o último salvo
		File currentDir = _currentFileWindow.getParentFile();
		if ((currentDir == null) || !currentDir.exists())
		{
			currentDir = new File(ConfigWindow.FILE_SAVE_CURRENT_DIRECTORY);
		}
		fileSave.setCurrentDirectory(currentDir);
		
		// Sugere nome do arquivo atual com extensão .txt
		String currentName = _currentFileWindow.getName();
		int dotIndex = currentName.lastIndexOf('.');
		String suggestedName = (dotIndex > 0) ? currentName.substring(0, dotIndex) + ".txt" : currentName + ".txt";
		fileSave.setSelectedFile(new File(suggestedName));
		
		fileSave.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
		fileSave.setAcceptAllFileFilterUsed(false);
		fileSave.setPreferredSize(new Dimension(700, 500));
		
		final int ret = fileSave.showSaveDialog(this);
		if (ret == JFileChooser.APPROVE_OPTION)
		{
			File selectedFile = fileSave.getSelectedFile();
			// Garante que a extensão seja .txt
			if (!selectedFile.getName().toLowerCase().endsWith(".txt"))
			{
				selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".txt");
			}
			
			// TODO: Adicionar confirmação se o arquivo já existe? O JFileChooser faz isso.
			
			ConfigWindow.save("FILE_SAVE_CURRENT_DIRECTORY", selectedFile.getParent()); // Salva o diretório usado
			_progressTask = new SaveTxt(this, selectedFile);
			_executorService.execute(_progressTask);
		}
	}
	
	private void saveDatActionPerformed(ActionEvent evt)
	{
		if (_progressTask != null)
		{
			return;
		}
		
		if (_currentFileWindow == null)
		{
			addLogConsole("No file reference to save as DAT. Use 'Open' first.", true);
			return;
		}
		
		// TODO: Perguntar se quer sobrescrever o arquivo original?
		// Por enquanto, salva diretamente no arquivo aberto.
		addLogConsole("Saving changes to DAT file: " + _currentFileWindow.getName(), true);
		_progressTask = new SaveDat(this, _currentFileWindow, String.valueOf(_jComboBoxChronicle.getSelectedItem()));
		_executorService.execute(_progressTask);
	}
	
	private void abortActionPerformed(ActionEvent evt)
	{
		if (_progressTask == null)
		{
			return;
		}
		
		_progressTask.abort(); // Sinaliza para a tarefa abortar
		// A tarefa deve verificar isAborted() e parar.
		addLogConsole("---------------------------------------", true);
		addLogConsole("Abort request sent.", true);
		// O estado dos botões será atualizado quando a tarefa realmente parar (onAbortTask)
	}
	
	// Retorna o encriptador baseado na seleção ou no último usado
	public DatCrypter getEncryptor(File file)
	{
		DatCrypter crypter = null;
		String encryptorName = ConfigWindow.CURRENT_ENCRYPT; // Pega a seleção atual
		
		// Verifica se deve usar o detectado/original
		if ((encryptorName == null) || encryptorName.equalsIgnoreCase(".") || encryptorName.equalsIgnoreCase(SOURCE_ENCRYPT_TYPE_STR) || encryptorName.trim().isEmpty())
		{
			final DatCrypter lastDatDecryptor = OpenDat.getLastDatCrypter(file);
			if (lastDatDecryptor != null)
			{
				// Tenta encontrar o mesmo encriptador na lista carregada
				crypter = CryptVersionParser.getInstance().getEncryptKey(lastDatDecryptor.getName());
				if (crypter != null)
				{
					addLogConsole("Using source encryptor: " + crypter.getName(), false); // Log menos verboso
				}
				else
				{
					// Se não encontrar exatamente o mesmo, loga um aviso
					addLogConsole("Warning: Source encryptor '" + lastDatDecryptor.getName() + "' not found in current definitions. Using default or selected.", true);
					// Poderia tentar um fallback aqui, ou deixar o 'crypter' como null para cair no próximo else
				}
			}
			else
			{
				addLogConsole("Source encryptor not detected for file: " + file.getName(), true);
				// Cai para a lógica de seleção explícita se a detecção falhar
				encryptorName = String.valueOf(_jComboBoxEncrypt.getSelectedItem()); // Pega a seleção novamente
				if (encryptorName.equalsIgnoreCase(SOURCE_ENCRYPT_TYPE_STR))
				{
					addLogConsole("Cannot use 'Source' encryptor as it wasn't detected. Please select a specific encryptor.", true);
					return null; // Retorna null para indicar falha
				}
			}
		}
		
		// Se não usou o detectado OU se o detectado não foi encontrado
		if ((crypter == null) && !((encryptorName == null) || encryptorName.equalsIgnoreCase(".") || encryptorName.equalsIgnoreCase(SOURCE_ENCRYPT_TYPE_STR) || encryptorName.trim().isEmpty()))
		{
			crypter = CryptVersionParser.getInstance().getEncryptKey(encryptorName);
			if (crypter != null)
			{
				addLogConsole("Using selected encryptor: " + crypter.getName(), false);
			}
			else
			{
				addLogConsole("Error: Selected encryptor '" + encryptorName + "' not found!", true);
				// Retorna null para indicar falha
				return null;
			}
		}
		
		// Se ainda assim for nulo (ex: seleção inválida e sem detecção)
		if (crypter == null)
		{
			addLogConsole("Error: Could not determine encryptor for file: " + file.getName(), true);
		}
		
		return crypter;
	}
	
	// Salva a seleção do ComboBox na configuração
	private void saveComboBox(JComboBox<String> jComboBox, String param)
	{
		ConfigWindow.save(param, String.valueOf(jComboBox.getSelectedItem()));
	}
	
	// Chamado quando uma tarefa (OpenDat, SaveDat, etc.) começa
	public void onStartTask()
	{
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		_progressBar.setValue(0);
		_progressBar.setIndeterminate(true); // Mostra progresso indeterminado no início
		checkButtons(); // Desabilita controles
	}
	
	// Chamado pela tarefa para atualizar o progresso (0-100)
	public void onProgressTask(int val)
	{
		if (_progressBar.isIndeterminate())
		{
			_progressBar.setIndeterminate(false); // Muda para progresso determinado
			_progressBar.setStringPainted(true);
		}
		_progressBar.setValue(val);
	}
	
	// Chamado quando a tarefa termina com sucesso
	public void onStopTask()
	{
		_progressTask = null; // Libera a referência da tarefa
		_progressBar.setValue(100);
		_progressBar.setIndeterminate(false);
		checkButtons(); // Reabilita controles
		Toolkit.getDefaultToolkit().beep(); // Beep de conclusão
		setCursor(Cursor.getDefaultCursor());
	}
	
	// Chamado se a tarefa for abortada (ou falhar e chamar onAbortTask)
	public void onAbortTask()
	{
		if (_progressTask == null)
		{
			return; // Já foi tratado ou não havia tarefa
		}
		
		_progressTask = null; // Libera a referência
		_progressBar.setValue(0); // Reseta a barra
		_progressBar.setIndeterminate(false);
		addLogConsole("Task aborted or failed.", true); // Mensagem no log
		checkButtons(); // Reabilita controles
		setCursor(Cursor.getDefaultCursor());
	}
	
	// Habilita/Desabilita os controles da UI durante uma tarefa
	private void checkButtons()
	{
		final boolean taskRunning = (_progressTask != null);
		
		// Itera sobre os painéis que contêm os controles a serem gerenciados
		for (JPanel panel : _actionPanels)
		{
			for (Component c : panel.getComponents())
			{
				// Desabilita TUDO no painel se a tarefa estiver rodando
				c.setEnabled(!taskRunning);
			}
		}
		
		// Habilita/desabilita o botão Abort
		_abortTaskButton.setEnabled(taskRunning);
		
		// Lógica específica para os botões Save (só habilitados se houver arquivo aberto E nenhuma tarefa rodando)
		if (!taskRunning)
		{
			boolean fileIsOpen = (_currentFileWindow != null);
			_saveTxtButton.setEnabled(fileIsOpen);
			_saveDatButton.setEnabled(fileIsOpen);
		}
		else
		{
			// Garante que estejam desabilitados se a tarefa estiver rodando
			_saveTxtButton.setEnabled(false);
			_saveDatButton.setEnabled(false);
		}
	}
	
	/**
	 * CLASSE INTERNA PARA O SPLASH SCREEN (Movida para o final do arquivo por organização) * Esta versão tem fundo transparente e animação "slide-in" de baixo para cima. * VERSÃO "EXORCISTA": Desenha a imagem DIRETAMENTE na janela.
	 */
	private static class ModernSplashWindow extends JWindow
	{
		private final Point finalPos;
		private final int windowHeight;
		private final Image image; // Guarda a imagem
		
		public ModernSplashWindow()
		{
			// 1. Carrega a imagem PRIMEIRO para pegar as dimensões
			ImageIcon splashIcon = null;
			try
			{
				// ****** MUDE A IMAGEM AQUI ******
				splashIcon = new ImageIcon("." + File.separator + "images" + File.separator + "splash.png");
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Não foi possível carregar a imagem do splash.", e);
			}
			
			if ((splashIcon != null) && (splashIcon.getIconWidth() > 0))
			{
				this.image = splashIcon.getImage();
				setPreferredSize(new Dimension(splashIcon.getIconWidth(), splashIcon.getIconHeight()));
			}
			else
			{
				this.image = null; // Falha no carregamento
				setPreferredSize(new Dimension(300, 200)); // Tamanho de fallback
				LOGGER.warning("Usando tamanho de fallback para o splash.");
			}
			
			// 2. Torna a janela principal transparente
			setBackground(new Color(0, 0, 0, 0));
			
			pack(); // Aplica o setPreferredSize
			
			// 3. Calcula a Posição Final (Centro da Tela)
			setLocationRelativeTo(null);
			this.finalPos = getLocation();
			this.windowHeight = getHeight();
			
			// 4. Calcula a Posição Inicial (Fora da tela, abaixo)
			Point startPos = new Point(this.finalPos.x, this.finalPos.y + this.windowHeight);
			
			// 5. Move a janela para a posição inicial antes de ficar visível
			setLocation(startPos);
		}
		
		/**
		 * NOVO MÉTODO PAINT: Vamos desenhar a imagem diretamente na janela transparente. Isso ignora o ContentPane e o JLabel.
		 */
		@Override
		public void paint(Graphics g)
		{
			// 1. Limpa a tela com 100% de transparência (EXORCISMO)
			g.setColor(new Color(0, 0, 0, 0));
			g.fillRect(0, 0, getWidth(), getHeight());
			
			// 2. Desenha a imagem (se ela existir)
			if (this.image != null)
			{
				// Desenha a imagem centralizada (caso o tamanho da janela seja maior que a imagem)
				int x = (getWidth() - this.image.getWidth(null)) / 2;
				int y = (getHeight() - this.image.getHeight(null)) / 2;
				g.drawImage(this.image, x, y, this);
			}
			
			// NÃO CHAMAMOS super.paint(g) - Ele era o fantasma!
		}
		
		/**
		 * Move a janela de baixo para cima com base no progresso (0.0 a 1.0).
		 */
		public void setAnimationProgress(double progress) // progress é 0.0 (início) a 1.0 (fim)
		{
			progress = Math.max(0.0, Math.min(progress, 1.0)); // Garante 0.0 a 1.0
			
			// Calcula a nova posição Y
			int newY = (int) (this.finalPos.y + (this.windowHeight * (1.0 - progress)));
			
			setLocation(this.finalPos.x, newY);
		}
	}
	
	/**
	 * CLASSE INTERNA PARA NÚMEROS DE LINHA (Este é o local correto para ela)
	 */
	private static class LineNumberingTextArea extends JTextArea implements DocumentListener
	{
		private final JTextArea textArea;
		private int lastLines;
		
		public LineNumberingTextArea(JTextArea area)
		{
			lastLines = 0;
			textArea = area;
		}
		
		public void cleanUp()
		{
			setText("");
			removeAll();
			lastLines = 0;
		}
		
		private void updateText()
		{
			final int length = textArea.getLineCount();
			if (length == lastLines)
			{
				return;
			}
			
			lastLines = length;
			final StringBuilder lineNumbersTextBuilder = new StringBuilder();
			lineNumbersTextBuilder.append("1").append(System.lineSeparator());
			for (int line = 2; line <= length; ++line)
			{
				lineNumbersTextBuilder.append(line).append(System.lineSeparator());
			}
			setText(lineNumbersTextBuilder.toString());
		}
		
		@Override
		public void insertUpdate(DocumentEvent documentEvent)
		{
			SwingUtilities.invokeLater(this::updateText); // Garante execução na EDT
		}
		
		@Override
		public void removeUpdate(DocumentEvent documentEvent)
		{
			SwingUtilities.invokeLater(this::updateText); // Garante execução na EDT
		}
		
		@Override
		public void changedUpdate(DocumentEvent documentEvent)
		{
			// Geralmente não usado para JTextArea simples
		}
	}
}