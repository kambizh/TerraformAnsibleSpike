# Terraform Cloud Run Tasks with Ansible Integration

The goal of this work is to explore how we can extend Terraform workflows beyond pure infrastructure provisioning and integrate them with our automation ecosystem, particularly Ansible, in a controlled and reusable way.

# Problem Statement: 

In many real scenarios, provisioning infrastructure is only part of the job.
After resources are created, we might need configuration, compliance steps or etc.

If we perform those steps manually or handle them in separate pipelines.May creates fragmentation and reduces visibility.

So the question this PoC explores is:

## Can Terraform Run Tasks act as a bridge between infrastructure provisioning and downstream automation?



# What is Terrafrom RunTask : 

Think of a Terraform Run Task as a "Check point" you can plug into a Terraform Clound / Enterprise Run (plan/apply) to let an external service inspect, approve or enrich your change before it goes through. 

You can place the RunTask at these stages : 
 - Pre-plan 
 - Post_plan
 - Pre-apply
 - Post-apply

# Architecture Diagram

Let me quickly walk through the flow.

<img width="1115" height="362" alt="image" src="https://github.com/user-attachments/assets/c1675477-db8f-4f80-ad45-6afd91c00be1" />


A client submits Terraform configuration to TFE or HCP Terraform, TFC 

After the apply phase, a Run Task is triggered.
This is where our extension point exists.

The Run Task makes an HTTP call to an orchestration API — in this PoC, a lightweight Java service.

That orchestration layer decides what automation to trigger and forwards the request to Ansible.

Ansible executes the playbook and returns execution results.

The results are sent back through the orchestration API to the Run Task callback URL, so Terraform has visibility into the outcome.

So effectively, Terraform remains the control plane for provisioning, while Ansible handles deeper configuration tasks.

# Terraform Configuration
For this demo, I’m using a very simple Terraform configuration.
It’s intentionally minimal because the goal is to demonstrate the Run Task integration, not the infrastructure itself.

I’m passing a timestamp value to Terraform.
Since the timestamp always changes, Terraform detects a change every time, which guarantees an apply step.

And that apply step is what triggers our post-apply Run Task.
# Ansible Playbook
On the Ansible side, I prepared a simple playbook.
This playbook gathers system facts and prints information such as:

 - Ansible version
 - Python version
 - Host details

So it’s basically a lightweight validation playbook to prove orchestration works end-to-end.

Client :
runner-test/tfe-smoke/README.md

Java Endpoint : 
run-task-service/README.md



