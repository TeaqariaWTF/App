package org.andbootmgr.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toFile
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.andbootmgr.app.util.AbmTheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest

class WizardPageFactory(private val vm: WizardActivityState) {
	fun get(flow: String): List<IWizardPage> {
		return when (flow) {
			"droidboot" -> DroidBootWizardPageFactory(vm).get()
			"fix_droidboot" -> FixDroidBootWizardPageFactory(vm).get()
			"update_droidboot" -> UpdateDroidBootWizardPageFactory(vm).get()
			"create_part" -> CreatePartWizardPageFactory(vm).get()
			"backup_restore" -> BackupRestoreWizardPageFactory(vm).get()
			"update" -> UpdateFlowWizardPageFactory(vm).get()
			else -> listOf()
		}
	}
}

class WizardActivity : ComponentActivity() {
	private lateinit var vm: WizardActivityState
	private lateinit var newFile: ActivityResultLauncher<String>
	private var onFileCreated: ((Uri) -> Unit)? = null
	private lateinit var chooseFile: ActivityResultLauncher<String>
	private var onFileChosen: ((Uri) -> Unit)? = null
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		vm = WizardActivityState(intent.getStringExtra("codename")!!)
		vm.activity = this
		vm.deviceInfo = JsonDeviceInfoFactory(this).get(vm.codename)!!
		vm.logic = DeviceLogic(this)
		chooseFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
			if (uri == null) {
				Toast.makeText(this, getString(R.string.file_unavailable), Toast.LENGTH_LONG).show()
				onFileChosen = null
				return@registerForActivityResult
			}
			if (onFileChosen != null) {
				onFileChosen!!(uri)
				onFileChosen = null
			} else {
				Toast.makeText(
					this@WizardActivity,
					getString(R.string.internal_file_error1),
					Toast.LENGTH_LONG
				).show()
			}
		}
		newFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
			if (uri == null) {
				Toast.makeText(this, getString(R.string.file_unavailable), Toast.LENGTH_LONG).show()
				onFileCreated = null
				return@registerForActivityResult
			}
			if (onFileCreated != null) {
				onFileCreated!!(uri)
				onFileCreated = null
			} else {
				Toast.makeText(
					this@WizardActivity,
					getString(R.string.internal_file_error1),
					Toast.LENGTH_LONG
				).show()
			}
		}
		val wizardPages = WizardPageFactory(vm).get(intent.getStringExtra("flow")!!)
		setContent {
			vm.navController = rememberNavController()
			AbmTheme {
				// A surface container using the 'background' color from the theme
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					Column(verticalArrangement = Arrangement.SpaceBetween,
						modifier = Modifier.fillMaxWidth()
					) {
						NavHost(
							navController = vm.navController,
							startDestination = "start",
							modifier = Modifier
								.fillMaxWidth()
								.weight(1.0f)
						) {
							for (i in wizardPages) {
								composable(i.name) {
									if (!vm.btnsOverride) {
										vm.prevText.value = i.prev.text
										vm.nextText.value = i.next.text
										vm.onPrev.value = i.prev.onClick
										vm.onNext.value = i.next.onClick
									}
									i.run()
								}
							}
						}
						Box(Modifier.fillMaxWidth()) {
							BtnsRow(vm)
						}
					}
				}
			}
		}
	}

	@SuppressLint("MissingSuperCall")
	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		vm.onPrev.value(this)
	}

	fun navigate(next: String) {
		vm.navigate(next)
	}
	fun chooseFile(mime: String, callback: (Uri) -> Unit) {
		if (onFileChosen != null) {
			Toast.makeText(
				this,
				getString(R.string.internal_file_error2),
				Toast.LENGTH_LONG
			).show()
			return
		}
		onFileChosen = callback
		chooseFile.launch(mime)
	}
	fun createFile(name: String, callback: (Uri) -> Unit) {
		if (onFileCreated != null) {
			Toast.makeText(
				this,
				getString(R.string.internal_file_error2),
				Toast.LENGTH_LONG
			).show()
			return
		}
		onFileCreated = callback
		newFile.launch(name)
	}
}

private class ExpectedDigestInputStream(stream: InputStream?,
                                        digest: MessageDigest?,
                                        private val expectedDigest: String
) : DigestInputStream(stream, digest) {
	@OptIn(ExperimentalStdlibApi::class)
	fun doAssert() {
		val hash = digest.digest().toHexString()
		if (hash != expectedDigest)
			throw IllegalArgumentException("digest $hash does not match expected hash $expectedDigest")
	}
}

class WizardActivityState(val codename: String) {
	var btnsOverride = false
	lateinit var navController: NavHostController
	lateinit var activity: WizardActivity
	lateinit var logic: DeviceLogic
	lateinit var deviceInfo: DeviceInfo
	private var current = mutableStateOf("start")
	var prevText = mutableStateOf("")
	var nextText = mutableStateOf("")
	var onPrev: MutableState<(WizardActivity) -> Unit> = mutableStateOf({})
	var onNext: MutableState<(WizardActivity) -> Unit> = mutableStateOf({})

	var flashes: HashMap<String, Pair<Uri, String?>> = HashMap()
	var texts: HashMap<String, String> = HashMap()

	fun navigate(next: String) {
		btnsOverride = false
		current.value = next
		navController.navigate(current.value)
	}

	fun copy(inputStream: InputStream, outputStream: OutputStream): Long {
		var nread = 0L
		val buf = ByteArray(8192)
		var n: Int
		while (inputStream.read(buf).also { n = it } > 0) {
			outputStream.write(buf, 0, n)
			nread += n.toLong()
		}
		inputStream.close()
		outputStream.flush()
		outputStream.close()
		if (inputStream is ExpectedDigestInputStream)
			inputStream.doAssert()
		return nread
	}

	fun flashStream(flashType: String): InputStream {
		return flashes[flashType]?.let {
			val i = when (it.first.scheme) {
				"content" ->
					activity.contentResolver.openInputStream(it.first)
						?: throw IOException("in == null")
				"file" ->
					FileInputStream(it.first.toFile())
				"http", "https" ->
					URL(it.first.toString()).openStream()
				else -> null
			}
			if (it.second != null)
				ExpectedDigestInputStream(i, MessageDigest.getInstance("SHA-256"), it.second!!)
			else i
		} ?: throw IllegalArgumentException()
	}

	fun copyUnpriv(inputStream: InputStream, output: File) {
		Files.copy(inputStream, output.toPath(), StandardCopyOption.REPLACE_EXISTING)
		inputStream.close()
	}

	fun copyPriv(inputStream: InputStream, output: File) {
		val outStream = SuFileOutputStream.open(output)
		copy(inputStream, outStream)
	}
}

class NavButton(val text: String, val onClick: (WizardActivity) -> Unit)
class WizardPage(override val name: String, override val prev: NavButton,
                       override val next: NavButton, override val run: @Composable () -> Unit
) : IWizardPage

interface IWizardPage {
	val name: String
	val prev: NavButton
	val next: NavButton
	val run: @Composable () -> Unit
}

@Composable
private fun BtnsRow(vm: WizardActivityState) {
	Row {
		TextButton(onClick = {
			vm.onPrev.value(vm.activity)
		}, modifier = Modifier.weight(1f, true)) {
			Text(vm.prevText.value)
		}
		TextButton(onClick = {
			vm.onNext.value(vm.activity)
		}, modifier = Modifier.weight(1f, true)) {
			Text(vm.nextText.value)
		}
	}
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
	val vm = WizardActivityState("null")
	AbmTheme {
		// A surface container using the 'background' color from the theme
		Surface(
			modifier = Modifier.fillMaxSize(),
			color = MaterialTheme.colorScheme.background
		) {
			Column {
				Box(
					Modifier
						.fillMaxWidth()
						.weight(1.0f)) {
					//Select(vm)
				}
				Box(Modifier.fillMaxWidth()) {
					BtnsRow(vm)
				}
			}
		}
	}
}

/* Monospace auto-scrolling text view, fed using MutableList<String>, catching exceptions and running logic on a different thread */
@Composable
fun Terminal(vm: WizardActivityState, logFile: String? = null, action: suspend (MutableList<String>) -> Unit) {
	val scrollH = rememberScrollState()
	val scrollV = rememberScrollState()
	val scope = rememberCoroutineScope()
	val text = remember { mutableStateOf("") }
	val ctx = LocalContext.current
	DisposableEffect(Unit) {
		val log = logFile?.let { FileOutputStream(File(ctx.externalCacheDir, it)) }
		// Budget CallbackList
		val s = object : MutableList<String> {
			val internalList = ArrayList<String>()
			override val size: Int
				get() = internalList.size

			override fun contains(element: String): Boolean {
				return internalList.contains(element)
			}

			override fun containsAll(elements: Collection<String>): Boolean {
				return internalList.containsAll(elements)
			}

			override fun get(index: Int): String {
				return internalList[index]
			}

			override fun indexOf(element: String): Int {
				return internalList.indexOf(element)
			}

			override fun isEmpty(): Boolean {
				return internalList.isEmpty()
			}

			override fun iterator(): MutableIterator<String> {
				return internalList.iterator()
			}

			override fun lastIndexOf(element: String): Int {
				return internalList.lastIndexOf(element)
			}

			override fun add(element: String): Boolean {
				onAdd(element)
				return internalList.add(element)
			}

			override fun add(index: Int, element: String) {
				onAdd(element)
				return internalList.add(index, element)
			}

			override fun addAll(index: Int, elements: Collection<String>): Boolean {
				for (i in elements) {
					onAdd(i)
				}
				return internalList.addAll(index, elements)
			}

			override fun addAll(elements: Collection<String>): Boolean {
				for (i in elements) {
					onAdd(i)
				}
				return internalList.addAll(elements)
			}

			override fun clear() {
				return internalList.clear()
			}

			override fun listIterator(): MutableListIterator<String> {
				return internalList.listIterator()
			}

			override fun listIterator(index: Int): MutableListIterator<String> {
				return internalList.listIterator(index)
			}

			override fun remove(element: String): Boolean {
				return internalList.remove(element)
			}

			override fun removeAll(elements: Collection<String>): Boolean {
				return internalList.removeAll(elements.toSet())
			}

			override fun removeAt(index: Int): String {
				return internalList.removeAt(index)
			}

			override fun retainAll(elements: Collection<String>): Boolean {
				return internalList.retainAll(elements.toSet())
			}

			override fun set(index: Int, element: String): String {
				return internalList.set(index, element)
			}

			override fun subList(fromIndex: Int, toIndex: Int): MutableList<String> {
				return internalList.subList(fromIndex, toIndex)
			}

			fun onAdd(element: String) {
				vm.activity.runOnUiThread {
					text.value += element + "\n"
					scope.launch {
						delay(200) // Give it time to re-measure
						scrollV.animateScrollTo(scrollV.maxValue)
						scrollH.animateScrollTo(0)
					}
				}
				log?.write((element + "\n").encodeToByteArray())
			}

		}
		CoroutineScope(Dispatchers.Default).launch {
			try {
				action(s)
			} catch (e: Throwable) {
				s.add(vm.activity.getString(R.string.term_failure))
				s.add(vm.activity.getString(R.string.dev_details))
				s.add(Log.getStackTraceString(e))
			}
		}
		onDispose {
			log?.close()
		}
	}
	Text(text.value, modifier = Modifier
		.fillMaxSize()
		.horizontalScroll(scrollH)
		.verticalScroll(scrollV)
		.padding(10.dp), fontFamily = FontFamily.Monospace)
}