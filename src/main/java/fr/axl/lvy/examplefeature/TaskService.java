package fr.axl.lvy.examplefeature;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  private final TaskRepository taskRepository;

  TaskService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional
  public void createTask(String description, @Nullable LocalDate dueDate) {
    var task = new Task(description, Instant.now());
    task.setDueDate(dueDate);
    taskRepository.saveAndFlush(task);
  }

  @Transactional(readOnly = true)
  public List<Task> list(Pageable pageable) {
    return taskRepository.findAllBy(pageable).toList();
  }
}
