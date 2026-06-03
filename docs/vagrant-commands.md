# Vagrant Commands

This document contains the Vagrant commands used to manage the local Kubernetes cluster for this project.

Run these commands from:

```powershell
cd C:\repository\spark-operations\spark-operations\vagrant-kubeadm-kubernetes
```

## VMware Utility

This project uses the VMware provider. Vagrant needs the local `vagrant-vmware-utility` service running.

Check the service:

```powershell
Get-Service VagrantVMware
```

Start the service:

```powershell
Start-Service VagrantVMware
```

Restart the service:

```powershell
Restart-Service VagrantVMware
```

Check the VMware utility port:

```powershell
Test-NetConnection 127.0.0.1 -Port 9922
```

Expected result:

```text
TcpTestSucceeded: True
```

If the service cannot be started, open PowerShell as Administrator.

## Basic Lifecycle

Start all virtual machines:

```powershell
vagrant up
```

Start only the control plane:

```powershell
vagrant up controlplane
```

Start one worker node:

```powershell
vagrant up node01
```

Show VM status:

```powershell
vagrant status
```

Stop all VMs:

```powershell
vagrant halt
```

Stop one VM:

```powershell
vagrant halt node01
```

Restart all VMs:

```powershell
vagrant reload
```

Restart one VM:

```powershell
vagrant reload controlplane
```

Destroy all VMs:

```powershell
vagrant destroy -f
```

Destroy one VM:

```powershell
vagrant destroy -f node02
```

## SSH Access

Connect to the control plane:

```powershell
vagrant ssh controlplane
```

Connect to worker node 1:

```powershell
vagrant ssh node01
```

Connect to worker node 2:

```powershell
vagrant ssh node02
```

Run one command on the control plane:

```powershell
vagrant ssh controlplane -c "kubectl get nodes"
```

Show SSH configuration:

```powershell
vagrant ssh-config
```

Show SSH configuration for one VM:

```powershell
vagrant ssh-config controlplane
```

## Provisioning

Run provisioning again for all VMs:

```powershell
vagrant provision
```

Run provisioning again for the control plane:

```powershell
vagrant provision controlplane
```

Restart and provision:

```powershell
vagrant reload --provision
```

Start and force provisioning:

```powershell
vagrant up --provision
```

Use provisioning when scripts changed under:

```text
vagrant-kubeadm-kubernetes/scripts/
```

## Kubernetes Commands Through Vagrant

Check nodes:

```powershell
vagrant ssh controlplane -c "kubectl get nodes -o wide"
```

Check all pods:

```powershell
vagrant ssh controlplane -c "kubectl get pods -A"
```

Check services:

```powershell
vagrant ssh controlplane -c "kubectl get svc -A"
```

Check Spark pods:

```powershell
vagrant ssh controlplane -c "kubectl get pods -n default"
```

Show logs from a pod:

```powershell
vagrant ssh controlplane -c "kubectl logs <pod-name>"
```

Follow logs from a pod:

```powershell
vagrant ssh controlplane -c "kubectl logs -f <pod-name>"
```

Describe a pod:

```powershell
vagrant ssh controlplane -c "kubectl describe pod <pod-name>"
```

Delete a Kubernetes job:

```powershell
vagrant ssh controlplane -c "kubectl delete job spark-submit-app-store-raw-ingestion --ignore-not-found"
```

Delete Spark pods:

```powershell
vagrant ssh controlplane -c "kubectl delete pod -l spark-role=driver --ignore-not-found"
vagrant ssh controlplane -c "kubectl delete pod -l spark-role=executor --ignore-not-found"
```

Apply a manifest from Windows through the control plane:

```powershell
Get-Content -Raw -LiteralPath ..\spark-job-batch\k8s\spark-service-account.yaml | vagrant ssh controlplane -c "kubectl apply -f -"
```

## Snapshots

Create a snapshot before testing risky changes:

```powershell
vagrant snapshot save clean-k8s-cluster
```

List snapshots:

```powershell
vagrant snapshot list
```

Restore a snapshot:

```powershell
vagrant snapshot restore clean-k8s-cluster
```

Delete a snapshot:

```powershell
vagrant snapshot delete clean-k8s-cluster
```

## Troubleshooting

Validate the Vagrantfile:

```powershell
vagrant validate
```

Show global Vagrant environments:

```powershell
vagrant global-status
```

Clean stale global status entries:

```powershell
vagrant global-status --prune
```

Check installed plugins:

```powershell
vagrant plugin list
```

Update the VMware provider plugin:

```powershell
vagrant plugin update vagrant-vmware-desktop
```

If Vagrant cannot connect to `127.0.0.1:9922`, restart the VMware utility service:

```powershell
Restart-Service VagrantVMware
```

If Kubernetes is running but local Windows `kubectl` cannot connect, use `kubectl` through Vagrant:

```powershell
vagrant ssh controlplane -c "kubectl get nodes"
```

## Recommended Daily Flow

Start the cluster:

```powershell
vagrant up
```

Verify Kubernetes:

```powershell
vagrant ssh controlplane -c "kubectl get nodes"
```

Run Spark manifests through the helper script:

```powershell
cd C:\repository\spark-operations\spark-operations\spark-job-batch
.\scripts\run-k8s-job.ps1
```

Stop the cluster when finished:

```powershell
cd C:\repository\spark-operations\spark-operations\vagrant-kubeadm-kubernetes
vagrant halt
```
