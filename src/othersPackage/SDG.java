package othersPackage;

import org.eclipse.jdt.core.dom.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class SDG
{
    private String projectPath;
    private int criterionLineNumber;
    private String criterionFilePath;

    public GraphNode sdgRoot = new GraphNode();
    public List<GraphNode> classRoots = new ArrayList<>();
    Map<String,GraphNode> mapForPathClassRoot = new HashMap<>();

    public SDG(String projectPath, String criterionFilePath, int criterionLineNumber) {
        this.projectPath = projectPath;
        this.criterionFilePath = criterionFilePath;
        this.criterionLineNumber = criterionLineNumber;
    }

    ASTNode startingNode;
    Set<ASTNode> nodesForBackwardSlicing = new TreeSet<>(Comparator.comparing(ASTNode::getStartPosition));
    Set<ASTNode> nodesForForwardSlicing = new TreeSet<>(Comparator.comparing(ASTNode::getStartPosition));

    Map<String,Set<Integer>> forwardSlicingMapForClassLineNumbers = new HashMap<>();
    Map<String,Set<Integer>> backwardSlicingMapForClassLineNumbers = new HashMap<>();

    public void handleDerivedClasses() {
        for (GraphNode classRoot: classRoots)
        {
            for (GraphNode classRoot2: classRoots)
            {
                /*System.out.println(classRoot.node);
                System.out.println(classRoot2.node);
                System.out.println("--------------");*/
                if (((TypeDeclaration) classRoot.getNode()).getSuperclassType()!=null)
                {
                    //System.out.println("Dhukesi1");
                    String cRsuperclass = ((TypeDeclaration) classRoot.getNode()).getSuperclassType().toString();
                    String cR2class = ((TypeDeclaration)classRoot2.getNode()).getName().toString();

                    /*System.out.println(cRsuperclass);
                    System.out.println(cR2class);*/

                    if (cRsuperclass.equals(cR2class))
                    {
                        for (GraphNode node: classRoot2.children)
                        {
                            if (((MethodDeclaration)node.node).isConstructor())
                            {
                                continue;
                            }
                            else
                            {
                                int isInheritedMethod = 1;
                                for (MethodDeclaration m: ((TypeDeclaration) classRoot.getNode()).getMethods())
                                {
                                    if ((((MethodDeclaration)node.node).getName().toString()).equals(m.getName().toString()))
                                    {
                                        isInheritedMethod = 0;
                                        break;
                                    }
                                }

                                if (isInheritedMethod==1)
                                {
                                    /*System.out.println(classRoot.node);
                                    System.out.println(node.node);*/
                                    classRoot.children.add(node);
                                    node.parents.add(classRoot);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FolderProcessor folderProcessor;

    public void operations () throws IOException {
        folderProcessor = new FolderProcessor(projectPath);

        for (File javaFile: folderProcessor.getFiles())
        {
            System.out.println(javaFile);
            Operation op = new Operation(folderProcessor.getEnvironment());
            op.operations(javaFile.getAbsolutePath());
            classRoots.add(op.root);
            mapForPathClassRoot.put(javaFile.getAbsolutePath(), op.root);
        }

        for (GraphNode classRoot: classRoots)
        {
            sdgRoot.getChildren().add(classRoot);
            classRoot.getParents().add(sdgRoot);
        }

        handleDerivedClasses();



        //debug system->class->method
        /*for (GraphNode classRoot: sdgRoot.children)
        {
            System.out.println(classRoot.node);

            for (GraphNode methodRoot: classRoot.children)
            {
                System.out.println(methodRoot.node);
            }
        }

        System.out.println("-----------------");*/

        parser();
    }

    void recursionForForwardSlicing (GraphNode g)
    {
        /*if(g==null)
            return;*/

        if (forwardSlicingMapForClassLineNumbers.containsKey(g.classFilePath))
        {
            forwardSlicingMapForClassLineNumbers.get(g.classFilePath).add(((CompilationUnit) g.node.getRoot()).getLineNumber(g.node.getStartPosition()));
        }
        else
        {
            Set<Integer> tempSet = new TreeSet<>(Comparator.comparing(Integer::intValue));
            tempSet.add(((CompilationUnit) g.node.getRoot()).getLineNumber(g.node.getStartPosition()));
            forwardSlicingMapForClassLineNumbers.put(g.classFilePath, tempSet);
        }

        nodesForForwardSlicing.add(g.node);

        for(GraphNode gg : g.children)
        {
            int pickNode = 1;
            for (ASTNode fs: nodesForForwardSlicing)
            {
                if (gg.node.toString().equals(fs.toString())&&gg.node.getStartPosition()==fs.getStartPosition())
                {
                    pickNode = 0;
                }
            }

            if (pickNode == 1)
            {
                recursionForForwardSlicing(gg);
            }
        }
    }

    Set<GraphNode> visited = new HashSet<>();

    GraphNode getStartingNode (GraphNode node)
    {
        GraphNode foundStartingNode = null;

        //System.out.println(node.node);
        for(GraphNode g : node.children)
        {
            if(g.node.toString().equals(startingNode.toString())&&g.node.getStartPosition()==startingNode.getStartPosition())
            {
                return g;
            }

            int pickNode = 1;
            for (GraphNode n: visited)
            {
                if (g.node.toString().equals(n.node.toString())&&g.node.getStartPosition()==n.node.getStartPosition())
                {
                    pickNode = 0;
                }
            }

            if (pickNode == 1)
            {
                visited.add(g);
                foundStartingNode = getStartingNode(g);
            }

            if(foundStartingNode!=null)
                break;
        }
        return foundStartingNode;
    }

    void recursionForBackwardSlicing (GraphNode g)
    {
        if (backwardSlicingMapForClassLineNumbers.containsKey(g.classFilePath))
        {
            backwardSlicingMapForClassLineNumbers.get(g.classFilePath).add(((CompilationUnit) g.node.getRoot()).getLineNumber(g.node.getStartPosition()));
        }
        else
        {
            Set<Integer> tempSet = new TreeSet<>(Comparator.comparing(Integer::intValue));
            tempSet.add(((CompilationUnit) g.node.getRoot()).getLineNumber(g.node.getStartPosition()));
            backwardSlicingMapForClassLineNumbers.put(g.classFilePath, tempSet);
        }
        nodesForBackwardSlicing.add(g.node);

        if(classRoots.contains(g))
        {
            return;
        }

        for(GraphNode gg : g.parents)
        {
            int pickNode = 1;
            for (ASTNode bs: nodesForBackwardSlicing)
            {
                //System.out.println(bs.toString());
                //System.out.println(gg.node.toString());
                if (gg.node.toString().equals(bs.toString())&&gg.node.getStartPosition()==bs.getStartPosition())
                {
                    pickNode = 0;
                }
            }
            if (pickNode == 1)
            {
                recursionForBackwardSlicing(gg);
            }
        }
    }

    public String readFileToString(String filePath) throws IOException {
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = new BufferedReader(new FileReader(filePath));

        char[] buf = new char[10];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
        }

        reader.close();

        return  fileData.toString();
    }

    public void parser () throws IOException {

        GraphNode classRoot = mapForPathClassRoot.get(criterionFilePath);
        //System.out.println(classRoot.node);

        ASTParser parser = ASTParser.newParser(AST.JLS10);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setSource(readFileToString(criterionFilePath).toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setEnvironment(null, folderProcessor.getEnvironment(), null, true);
        //parser.setEnvironment(null, null, null, true);
        parser.setUnitName("Saal.java");
        final CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        cu.accept(new ASTVisitor() {

            public void preVisit (ASTNode node) {
                if(node instanceof Statement && !(node instanceof Block))
                {
                    startingNode = node;

                    if (((CompilationUnit) startingNode.getRoot()).getLineNumber(startingNode.getStartPosition())==criterionLineNumber)
                    {
                        GraphNode foundNode = getStartingNode(classRoot);
                        //System.out.println(foundNode.node);
                        System.out.print("Line Number: ");
                        System.out.println(((CompilationUnit) foundNode.node.getRoot()).getLineNumber(foundNode.node.getStartPosition()));
                        recursionForBackwardSlicing(foundNode);
                        recursionForForwardSlicing(foundNode);
                        System.out.println("Backward slicing:");
                        for (String filePath: backwardSlicingMapForClassLineNumbers.keySet())
                        {
                            System.out.println(filePath);
                            for (int lineNumber: backwardSlicingMapForClassLineNumbers.get(filePath))
                            {
                                System.out.println(lineNumber);
                            }
                        }
                        /*for(ASTNode astNode: nodesForBackwardSlicing)
                        {
                            //System.out.println(astNode);
                            System.out.println(((CompilationUnit) astNode.getRoot()).getLineNumber(astNode.getStartPosition()));
                        }*/
                        System.out.println("Forward slicing:");
                        for (String filePath: forwardSlicingMapForClassLineNumbers.keySet())
                        {
                            System.out.println(filePath);
                            for (int lineNumber: forwardSlicingMapForClassLineNumbers.get(filePath))
                            {
                                System.out.println(lineNumber);
                            }
                        }
                        /*for(ASTNode astNode: nodesForForwardSlicing)
                        {
                            //System.out.println(astNode);
                            System.out.println(((CompilationUnit) astNode.getRoot()).getLineNumber(astNode.getStartPosition()));
                        }*/
                        System.out.println("---------------------------------");
                        nodesForBackwardSlicing.clear();
                        nodesForForwardSlicing.clear();
                        visited.clear();
                    }
                }
            }

        });
    }
}
