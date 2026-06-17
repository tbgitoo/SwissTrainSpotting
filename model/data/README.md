# Dataset Preparation (Module 4)

This project uses two dataset profiles for training and validation:

- `hymenoptera` — public reference dataset (ants vs bees)
- `swiss_trains` — project-specific dataset (self-acquired images)

Raw image data is **not stored in the repository**. The following instructions describe how to prepare the datasets locally.

---

## 1. Reference dataset (`hymenoptera`)

### Source

The reference dataset is taken from the official PyTorch transfer learning tutorial:

- https://docs.pytorch.org/tutorials/beginner/transfer_learning_tutorial.html

Download URL:

- https://download.pytorch.org/tutorial/hymenoptera_data.zip

This dataset contains images of two classes:
- `ants`
- `bees`

---

### License

The dataset is distributed as part of PyTorch tutorial materials (BSD-licensed tutorial content).  
Individual images originate from publicly available sources and are intended for educational use.

Use is restricted to:
- local training
- educational / demonstration purposes

The dataset is **not redistributed** in this repository.

---

### Preparation

Download and extract:

```bash
cd model/data
wget https://download.pytorch.org/tutorial/hymenoptera_data.zip
unzip hymenoptera_data.zip
```

###Convert into folder-specific layout:


```bash
mkdir -p raw_hymenoptera

cp -r hymenoptera_data/train/ants raw_hymenoptera/
cp -r hymenoptera_data/train/bees raw_hymenoptera/

cp hymenoptera_data/val/ants/* raw_hymenoptera/ants/
cp hymenoptera_data/val/bees/* raw_hymenoptera/bees/
```
