package net.jp.ytake.embulk.input.kintone

import com.google.common.annotations.VisibleForTesting
import net.jp.ytake.embulk.input.kintone.client.Kintone
import org.embulk.config.{ConfigDiff, ConfigSource, TaskReport, TaskSource}
import org.embulk.spi.{Exec, InputPlugin, PageBuilder, PageOutput, Schema}
import org.embulk.util.config.ConfigMapperFactory
import org.slf4j.LoggerFactory

import scala.util.control.Breaks
import java.util

/**
 * for Kintone Input Plugin
 */
class KintoneInputPlugin extends InputPlugin {

  private val logger = LoggerFactory.getLogger(classOf[KintoneInputPlugin])
  private def configFactory = ConfigMapperFactory.builder().addDefaultModules().build();

  override def transaction(config: ConfigSource, control: InputPlugin.Control): ConfigDiff = {
    val task =  configFactory.createConfigMapper.map(config, classOf[PluginTask])
    val schema = task.getFields.toSchema
    val taskCount = 1
    resume(task.toTaskSource, schema, taskCount, control)
  }

  override def resume(taskSource: TaskSource, schema: Schema, taskCount: Int, control: InputPlugin.Control): ConfigDiff = {
    control.run(taskSource, schema, taskCount)
    configFactory.newConfigDiff
  }

  override def cleanup(taskSource: TaskSource, schema: Schema, taskCount: Int, successTaskReports: util.List[TaskReport]): Unit = {
    //
  }

  override def run(taskSource: TaskSource, schema: Schema, taskIndex: Int, output: PageOutput): TaskReport = {
    val task = configFactory.createTaskMapper().map(taskSource, classOf[PluginTask])
    try {
      val pageBuilder = getPageBuilder(schema, output)
      try {
        // 設定が正しいか
        Kintone.validateAuth(task)
        val client = Kintone.client(Kintone.configure(task))
        val cursor = new Operation(client)
        // cursorを使ってリクエスト送信
        var cursorResponse = cursor.makeCursor(task)

        val b = new Breaks
        b.breakable {
          while (true) {
            val response = cursor.retrieveResponseByCursor(cursorResponse)
            response.getRecords.forEach(row => {
              new Accessor(row)
              pageBuilder.flush()
            })
            pageBuilder.flush()
            if (response.hasNext) {
              // b.break
            }
          }
        }
        pageBuilder.finish()
      } finally if (pageBuilder != null) pageBuilder.close()
    }
    catch {
      case e: Exception =>
        logger.error(e.getMessage)
        throw e
    }
    configFactory.newTaskReport
  }

  override def guess(config: ConfigSource): ConfigDiff = configFactory.newConfigDiff

  @VisibleForTesting
  protected def getPageBuilder(schema: Schema, output: PageOutput): PageBuilder = Exec.getPageBuilder(
    Exec.getBufferAllocator,
    schema,
    output
  )
}